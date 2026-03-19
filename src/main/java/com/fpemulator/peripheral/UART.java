package com.fpemulator.peripheral;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * UART emulation for LPC1778 (UART0 – UART4).
 *
 * Register map (base address + offset):
 *   +0x000  RBR/THR/DLL  – Receive Buffer / Transmit Hold / Divisor Latch Low
 *   +0x004  DLM/IER      – Divisor Latch High / Interrupt Enable
 *   +0x008  IIR/FCR      – Interrupt ID / FIFO Control
 *   +0x00C  LCR          – Line Control
 *   +0x010  MCR          – Modem Control
 *   +0x014  LSR          – Line Status
 *   +0x018  MSR          – Modem Status
 *   +0x01C  SCR          – Scratch Pad
 *
 * The emulator exposes two queues:
 *   txQueue – bytes written by the firmware → external consumer (serial port)
 *   rxQueue – bytes injected from outside → read by firmware
 */
public class UART {

    private static final Logger LOG = Logger.getLogger(UART.class.getName());

    // LSR bits
    private static final int LSR_DR   = 0x01; // Data Ready
    private static final int LSR_THRE = 0x20; // Transmitter Holding Register Empty
    private static final int LSR_TEMT = 0x40; // Transmitter Empty

    private final int baseAddress;
    private final int index;

    // Registers
    private int ier;  // Interrupt Enable Register
    private int lcr;  // Line Control Register
    private int mcr;  // Modem Control Register
    private int scr;  // Scratch Pad Register
    private int dll;  // Divisor Latch Low
    private int dlm;  // Divisor Latch High

    // Queues
    private final BlockingQueue<Integer> rxQueue = new LinkedBlockingQueue<>(256);
    private final BlockingQueue<Integer> txQueue = new LinkedBlockingQueue<>(256);

    // External listener called when a byte is transmitted by firmware
    private Consumer<Integer> txListener;

    public UART(int index, int baseAddress) {
        this.index       = index;
        this.baseAddress = baseAddress;
        reset();
    }

    public void reset() {
        ier = 0;
        lcr = 0;
        mcr = 0;
        scr = 0;
        dll = 0;
        dlm = 0;
        rxQueue.clear();
        txQueue.clear();
    }

    /** Called by PeripheralBus when firmware reads a register. */
    public int read(int address) {
        int offset = address - baseAddress;
        boolean dlab = (lcr & 0x80) != 0;
        return switch (offset) {
            case 0x00 -> dlab ? dll : readRBR();   // RBR or DLL
            case 0x04 -> dlab ? dlm : ier;          // DLM or IER
            case 0x08 -> 0x01;                       // IIR: no interrupt pending
            case 0x0C -> lcr;
            case 0x10 -> mcr;
            case 0x14 -> buildLSR();
            case 0x18 -> 0xB0;                       // MSR: CTS, DSR, DCD active
            case 0x1C -> scr;
            default   -> 0;
        };
    }

    /** Called by PeripheralBus when firmware writes a register. */
    public void write(int address, int value) {
        int offset = address - baseAddress;
        boolean dlab = (lcr & 0x80) != 0;
        switch (offset) {
            case 0x00 -> { if (dlab) dll = value & 0xFF; else transmit(value & 0xFF); }
            case 0x04 -> { if (dlab) dlm = value & 0xFF; else ier = value & 0x0F; }
            case 0x08 -> { /* FCR – FIFO control, acknowledge only */ }
            case 0x0C -> lcr = value & 0xFF;
            case 0x10 -> mcr = value & 0x1F;
            case 0x1C -> scr = value & 0xFF;
        }
    }

    private int readRBR() {
        Integer b = rxQueue.poll();
        return b != null ? b : 0;
    }

    private void transmit(int b) {
        txQueue.offer(b);
        if (txListener != null) txListener.accept(b);
        LOG.finest(String.format("UART%d TX: 0x%02X (%c)", index, b, (b >= 0x20 && b < 0x7F) ? (char)b : '.'));
    }

    private int buildLSR() {
        int lsr = LSR_THRE | LSR_TEMT; // TX always ready
        if (!rxQueue.isEmpty()) lsr |= LSR_DR;
        return lsr;
    }

    /** Inject a byte into the RX FIFO (as if received from serial port). */
    public void receive(int b) {
        rxQueue.offer(b & 0xFF);
    }

    /** Inject bytes from a byte array. */
    public void receive(byte[] data) {
        for (byte b : data) receive(b & 0xFF);
    }

    /** Poll the next byte from the TX queue (returns -1 if empty). */
    public int pollTx() {
        Integer b = txQueue.poll();
        return b != null ? b : -1;
    }

    public boolean hasTxData() { return !txQueue.isEmpty(); }

    public void setTxListener(Consumer<Integer> listener) { this.txListener = listener; }

    public int getBaseAddress() { return baseAddress; }
    public int getIndex()       { return index; }
}
