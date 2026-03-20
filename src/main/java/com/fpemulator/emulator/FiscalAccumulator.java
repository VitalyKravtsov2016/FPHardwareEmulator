package com.fpemulator.emulator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Emulates the Fiscal Accumulator (FN - Фискальный Накопитель).
 * Implements the FN protocol as described in the ФН documentation.
 */
public class FiscalAccumulator {

    public enum FnState {
        NOT_READY,
        READY,
        OPEN_SESSION,
        CLOSED_SESSION,
        EXHAUSTED,
        INVALID
    }

    // FN Commands (as per protocol documentation)
    public static final byte CMD_GET_FN_STATUS      = (byte) 0x01;
    public static final byte CMD_GET_FN_NUMBER       = (byte) 0x02;
    public static final byte CMD_OPEN_SESSION        = (byte) 0x10;
    public static final byte CMD_CLOSE_SESSION       = (byte) 0x14;
    public static final byte CMD_OPEN_RECEIPT        = (byte) 0x21;
    public static final byte CMD_ADD_ITEM            = (byte) 0x22;
    public static final byte CMD_CLOSE_RECEIPT       = (byte) 0x24;
    public static final byte CMD_CANCEL_RECEIPT      = (byte) 0x25;
    public static final byte CMD_GET_LAST_FD         = (byte) 0x30;

    // FN Response codes
    public static final byte OK                      = (byte) 0x00;
    public static final byte ERROR_INVALID_CMD       = (byte) 0x01;
    public static final byte ERROR_INVALID_STATE     = (byte) 0x02;
    public static final byte ERROR_EXHAUSTED         = (byte) 0x03;

    private FnState state = FnState.READY;
    private String fnNumber = "9999078900001234";
    private int sessionNumber = 0;
    private int documentNumber = 0;
    private int receiptNumber = 0;
    private boolean receiptOpen = false;
    private final List<String> currentReceiptLines = new ArrayList<>();
    private final List<FnDocument> documents = new ArrayList<>();
    private long remainingDocuments = 9999;

    public static class FnDocument {
        public final int number;
        public final String type;
        public final LocalDateTime dateTime;
        public final byte[] data;

        public FnDocument(int number, String type, byte[] data) {
            this.number = number;
            this.type = type;
            this.dateTime = LocalDateTime.now();
            this.data = data;
        }
    }

    public static class FnResponse {
        public final byte errorCode;
        public final byte[] data;

        public FnResponse(byte errorCode, byte[] data) {
            this.errorCode = errorCode;
            this.data = data;
        }

        public boolean isOk() {
            return errorCode == OK;
        }
    }

    /**
     * Process a command sent to the FN via the protocol.
     */
    public FnResponse processCommand(byte command, byte[] params) {
        switch (command) {
            case CMD_GET_FN_STATUS:
                return getFnStatus();
            case CMD_GET_FN_NUMBER:
                return handleGetFnNumber();
            case CMD_OPEN_SESSION:
                return openSession();
            case CMD_CLOSE_SESSION:
                return closeSession();
            case CMD_OPEN_RECEIPT:
                return openReceipt(params);
            case CMD_ADD_ITEM:
                return addItem(params);
            case CMD_CLOSE_RECEIPT:
                return closeReceipt(params);
            case CMD_CANCEL_RECEIPT:
                return cancelReceipt();
            case CMD_GET_LAST_FD:
                return getLastFiscalDocument();
            default:
                return new FnResponse(ERROR_INVALID_CMD, new byte[0]);
        }
    }

    private FnResponse getFnStatus() {
        byte stateCode = switch (state) {
            case READY          -> 0x01;
            case OPEN_SESSION   -> 0x03;
            case CLOSED_SESSION -> 0x07;
            case EXHAUSTED      -> 0x0F;
            default             -> 0x00;
        };
        byte[] data = new byte[]{
            stateCode,
            (byte) ((remainingDocuments >> 8) & 0xFF),
            (byte) (remainingDocuments & 0xFF)
        };
        return new FnResponse(OK, data);
    }

    private FnResponse handleGetFnNumber() {
        byte[] fnNumberBytes = fnNumber.getBytes();
        return new FnResponse(OK, fnNumberBytes);
    }

    private FnResponse openSession() {
        if (state == FnState.OPEN_SESSION) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        if (state == FnState.EXHAUSTED) {
            return new FnResponse(ERROR_EXHAUSTED, new byte[0]);
        }
        state = FnState.OPEN_SESSION;
        sessionNumber++;
        receiptNumber = 0;
        return new FnResponse(OK, new byte[]{(byte)(sessionNumber & 0xFF)});
    }

    private FnResponse closeSession() {
        if (state != FnState.OPEN_SESSION) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        if (receiptOpen) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        state = FnState.CLOSED_SESSION;
        documentNumber++;
        FnDocument doc = new FnDocument(documentNumber, "SESSION_CLOSE", new byte[0]);
        documents.add(doc);
        remainingDocuments--;
        return new FnResponse(OK, new byte[]{(byte)(sessionNumber & 0xFF)});
    }

    private FnResponse openReceipt(byte[] params) {
        if (state != FnState.OPEN_SESSION) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        if (receiptOpen) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        receiptOpen = true;
        receiptNumber++;
        currentReceiptLines.clear();
        String type = (params != null && params.length > 0) ? getReceiptTypeName(params[0]) : "SALE";
        currentReceiptLines.add("=== " + type + " ===");
        currentReceiptLines.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
        currentReceiptLines.add("Чек №" + receiptNumber + " Смена №" + sessionNumber);
        currentReceiptLines.add("--------------------------------");
        return new FnResponse(OK, new byte[]{(byte)(receiptNumber & 0xFF)});
    }

    private String getReceiptTypeName(byte type) {
        return switch (type) {
            case 0x01 -> "ПРОДАЖА";
            case 0x02 -> "ВОЗВРАТ ПРОДАЖИ";
            case 0x03 -> "РАСХОД";
            case 0x04 -> "ВОЗВРАТ РАСХОДА";
            default   -> "ОПЕРАЦИЯ";
        };
    }

    private FnResponse addItem(byte[] params) {
        if (!receiptOpen) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        if (params == null || params.length < 4) {
            return new FnResponse(ERROR_INVALID_CMD, new byte[0]);
        }
        // Simple item: first 2 bytes = price (kopecks), last 2 bytes = quantity*1000
        int price = ((params[0] & 0xFF) << 8) | (params[1] & 0xFF);
        int qty = ((params[2] & 0xFF) << 8) | (params[3] & 0xFF);
        double priceRub = price / 100.0;
        double qtyReal = qty / 1000.0;
        double sum = priceRub * qtyReal;
        String line = String.format("%.3f x %.2f = %.2f руб.", qtyReal, priceRub, sum);
        currentReceiptLines.add(line);
        return new FnResponse(OK, new byte[0]);
    }

    private FnResponse closeReceipt(byte[] params) {
        if (!receiptOpen) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        currentReceiptLines.add("--------------------------------");
        // Parse total if provided
        if (params != null && params.length >= 4) {
            int total = ((params[0] & 0xFF) << 24) | ((params[1] & 0xFF) << 16)
                      | ((params[2] & 0xFF) << 8)  | (params[3] & 0xFF);
            double totalRub = total / 100.0;
            currentReceiptLines.add(String.format("ИТОГО:          %10.2f руб.", totalRub));
        }
        currentReceiptLines.add("ФН: " + fnNumber);
        currentReceiptLines.add("ФД №" + (documentNumber + 1));
        currentReceiptLines.add("ФПД: " + generateFpd());
        currentReceiptLines.add("================================");

        receiptOpen = false;
        documentNumber++;
        byte[] receiptData = String.join("\n", currentReceiptLines).getBytes();
        FnDocument doc = new FnDocument(documentNumber, "RECEIPT", receiptData);
        documents.add(doc);
        remainingDocuments--;

        return new FnResponse(OK, receiptData);
    }

    private FnResponse cancelReceipt() {
        if (!receiptOpen) {
            return new FnResponse(ERROR_INVALID_STATE, new byte[0]);
        }
        receiptOpen = false;
        currentReceiptLines.clear();
        return new FnResponse(OK, new byte[0]);
    }

    private FnResponse getLastFiscalDocument() {
        if (documents.isEmpty()) {
            return new FnResponse(OK, new byte[0]);
        }
        FnDocument last = documents.get(documents.size() - 1);
        return new FnResponse(OK, last.data);
    }

    private String generateFpd() {
        // Simplified FPD (Fiscal Proof Document) generation
        long fpd = (long)(Math.random() * 0xFFFFFFFFL);
        return String.format("%010d", fpd % 10_000_000_000L);
    }

    // Getters
    public FnState getState() { return state; }
    public String getFnNumber() { return fnNumber; }
    public int getSessionNumber() { return sessionNumber; }
    public int getDocumentNumber() { return documentNumber; }
    public int getReceiptNumber() { return receiptNumber; }
    public boolean isReceiptOpen() { return receiptOpen; }
    public long getRemainingDocuments() { return remainingDocuments; }
    public List<String> getCurrentReceiptLines() { return new ArrayList<>(currentReceiptLines); }
    public List<FnDocument> getDocuments() { return new ArrayList<>(documents); }

    public void reset() {
        state = FnState.READY;
        sessionNumber = 0;
        documentNumber = 0;
        receiptNumber = 0;
        receiptOpen = false;
        currentReceiptLines.clear();
        documents.clear();
        remainingDocuments = 9999;
    }
}
