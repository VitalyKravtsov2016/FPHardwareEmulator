package com.fpemulator.printer;

import java.util.ArrayList;
import java.util.List;

/**
 * Emulates the thermal printer of the fiscal register.
 * Collects printed lines and notifies listeners.
 */
public class PrinterEmulator {

    private final List<String> printBuffer = new ArrayList<>();
    private final List<PrinterListener> listeners = new ArrayList<>();

    public interface PrinterListener {
        void onLinePrinted(String line);
        void onCut();
    }

    public void addListener(PrinterListener listener) {
        listeners.add(listener);
    }

    /**
     * Send text to the printer buffer.
     */
    public void print(String text) {
        if (text == null || text.isEmpty()) return;
        String[] lines = text.split("\n");
        for (String line : lines) {
            printBuffer.add(line);
            for (PrinterListener listener : listeners) {
                listener.onLinePrinted(line);
            }
        }
    }

    /**
     * Simulate a paper cut after receipt.
     */
    public void cut() {
        printBuffer.add("- - - - - - - - - - - - - - - -");
        for (PrinterListener listener : listeners) {
            listener.onCut();
        }
    }

    /**
     * Get all printed lines since last clear.
     */
    public List<String> getPrintBuffer() {
        return new ArrayList<>(printBuffer);
    }

    /**
     * Get full receipt as a single string.
     */
    public String getFullReceiptText() {
        return String.join("\n", printBuffer);
    }

    public void clearBuffer() {
        printBuffer.clear();
    }
}
