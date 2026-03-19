package com.fpemulator.comport;

import com.fpemulator.fn.FiscalAccumulator;
import com.fpemulator.fn.FiscalRecord;
import com.fpemulator.peripheral.UART;

import java.util.logging.Logger;

/**
 * High-level protocol handler that bridges the virtual COM port with the
 * fiscal accumulator and the emulated UART peripheral.
 *
 * This class handles two directions:
 *
 *   1. External → Emulator:
 *      Bytes arriving on the VirtualComPort are forwarded into the emulated
 *      UART0 RX FIFO so that the firmware can process them.
 *
 *   2. Emulator → External:
 *      Bytes written to UART0's TX queue are polled and forwarded to the
 *      connected TCP client via VirtualComPort.
 *
 * Additionally, the protocol handler understands a simple high-level text
 * command set for the demo mode (when no firmware is loaded), allowing the
 * receipt window to be driven directly from the external client.
 *
 * Demo command format (newline-terminated UTF-8):
 *   OPEN_SHIFT                   – open fiscal shift
 *   CLOSE_SHIFT                  – close fiscal shift
 *   OPEN_RECEIPT [SALE|RETURN]   – open receipt
 *   ADD_ITEM name|qty|price|vat  – add item (qty in 1/1000, price in kopecks)
 *   CLOSE_RECEIPT cash|card      – close receipt
 *   CANCEL_RECEIPT               – cancel current receipt
 *   STATUS                       – query FN status
 */
public class ProtocolHandler {

    private static final Logger LOG = Logger.getLogger(ProtocolHandler.class.getName());

    private final VirtualComPort    comPort;
    private final UART              uart;
    private final FiscalAccumulator fn;

    // Line buffer for demo text protocol
    private final StringBuilder lineBuffer = new StringBuilder();

    public ProtocolHandler(VirtualComPort comPort, UART uart, FiscalAccumulator fn) {
        this.comPort = comPort;
        this.uart    = uart;
        this.fn      = fn;

        // Wire incoming bytes: COM port → UART RX  AND  demo protocol parser
        comPort.setReceiveCallback(b -> {
            uart.receive(b);
            processIncomingByte(b);
        });

        // Wire outgoing bytes: UART TX → COM port
        uart.setTxListener(b -> comPort.send(b));
    }

    // ── Polling (call from main loop / timer) ─────────────────────────────────

    /**
     * Forward any pending UART TX data to the COM port.
     * Should be called periodically (e.g. 10ms).
     */
    public void tick() {
        int b;
        while ((b = uart.pollTx()) != -1) {
            comPort.send(b);
        }
    }

    // ── Demo text protocol ────────────────────────────────────────────────────

    private void processIncomingByte(int b) {
        if (b == '\n') {
            String line = lineBuffer.toString().trim();
            lineBuffer.setLength(0);
            if (!line.isEmpty()) handleDemoCommand(line);
        } else if (b != '\r') {
            lineBuffer.append((char) b);
        }
    }

    private void handleDemoCommand(String line) {
        LOG.info("Demo command: " + line);
        try {
            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toUpperCase();
            String arg = parts.length > 1 ? parts[1] : "";

            switch (cmd) {
                case "OPEN_SHIFT" -> {
                    fn.openShift();
                    sendResponse("OK OPEN_SHIFT shift=" + fn.getShiftNumber());
                }
                case "CLOSE_SHIFT" -> {
                    fn.closeShift();
                    sendResponse("OK CLOSE_SHIFT");
                }
                case "OPEN_RECEIPT" -> {
                    FiscalRecord.Type type = arg.equalsIgnoreCase("RETURN")
                            ? FiscalRecord.Type.RETURN : FiscalRecord.Type.SALE;
                    fn.openReceipt(type);
                    sendResponse("OK OPEN_RECEIPT receipt=" + fn.getReceiptNumber());
                }
                case "ADD_ITEM" -> {
                    String[] f = arg.split("\\|");
                    String name  = f.length > 0 ? f[0] : "Товар";
                    int    qty   = f.length > 1 ? Integer.parseInt(f[1].trim()) : 1000;
                    long   price = f.length > 2 ? Long.parseLong(f[2].trim()) : 0;
                    int    vat   = f.length > 3 ? Integer.parseInt(f[3].trim()) : 4;
                    fn.addItem(name, qty, price, vat);
                    sendResponse("OK ADD_ITEM");
                }
                case "CLOSE_RECEIPT" -> {
                    String[] f    = arg.split("\\|");
                    long cash = f.length > 0 ? Long.parseLong(f[0].trim()) : 0;
                    long card = f.length > 1 ? Long.parseLong(f[1].trim()) : 0;
                    FiscalRecord r = fn.closeReceipt(cash, card);
                    sendResponse("OK CLOSE_RECEIPT total=" + r.getTotalAmount()
                            + " fd=" + r.getFiscalDocNumber()
                            + " fp=" + r.getFiscalSign());
                }
                case "CANCEL_RECEIPT" -> {
                    fn.cancelReceipt();
                    sendResponse("OK CANCEL_RECEIPT");
                }
                case "STATUS" -> sendResponse(String.format(
                        "OK STATUS state=%s shift=%d receipt=%d docs=%d fn=%s",
                        fn.getState(), fn.getShiftNumber(),
                        fn.getReceiptNumber(), fn.getTotalDocCount(),
                        fn.getFnSerial()));
                default -> sendResponse("ERR UNKNOWN_COMMAND " + cmd);
            }
        } catch (Exception e) {
            sendResponse("ERR " + e.getMessage());
            LOG.warning("Demo command error: " + e.getMessage());
        }
    }

    private void sendResponse(String response) {
        byte[] bytes = (response + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        comPort.send(bytes);
    }
}
