package com.fpemulator.emulator;

import com.fpemulator.hardware.HardwareState;
import com.fpemulator.printer.PrinterEmulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Core emulator of a fiscal register (ФР).
 * Manages hardware state, FN communication, and printer output.
 */
public class FiscalRegister {

    public enum FrError {
        NONE,
        COVER_OPEN,
        PAPER_END,
        PAPER_ABSENT,
        FN_ERROR,
        FIRMWARE_NOT_LOADED,
        POWERED_OFF
    }

    private final HardwareState hardwareState;
    private final FiscalAccumulator fiscalAccumulator;
    private final PrinterEmulator printerEmulator;
    private final List<FrListener> listeners = new ArrayList<>();

    public interface FrListener {
        void onReceiptPrinted(String receiptText);
        void onError(FrError error, String message);
        void onLogMessage(String message);
    }

    public FiscalRegister(HardwareState hardwareState,
                          FiscalAccumulator fiscalAccumulator,
                          PrinterEmulator printerEmulator) {
        this.hardwareState = hardwareState;
        this.fiscalAccumulator = fiscalAccumulator;
        this.printerEmulator = printerEmulator;
    }

    public void addListener(FrListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FrListener listener) {
        listeners.remove(listener);
    }

    /**
     * Power on the device. Checks that firmware is loaded.
     */
    public boolean powerOn() {
        if (!hardwareState.isFirmwareLoaded()) {
            notifyError(FrError.FIRMWARE_NOT_LOADED, "Прошивка не загружена. Загрузите файл .bin");
            return false;
        }
        hardwareState.setPowerState(HardwareState.PowerState.ON);
        log("Питание включено. Загружена прошивка: " + hardwareState.getFirmwarePath()
            + " (" + hardwareState.getFirmwareSize() + " байт)");
        log("ФН: " + (hardwareState.isFnConnected() ? "подключен" : "не подключен"));

        if (hardwareState.isFnConnected()) {
            FiscalAccumulator.FnResponse status = fiscalAccumulator.processCommand(
                FiscalAccumulator.CMD_GET_FN_STATUS, null
            );
            if (status.isOk()) {
                log("ФН статус: OK, состояние=" + fiscalAccumulator.getState()
                    + ", оставшихся ФД=" + fiscalAccumulator.getRemainingDocuments());
            }
        }
        checkHardwareErrors();
        return true;
    }

    /**
     * Power off the device.
     */
    public void powerOff() {
        hardwareState.setPowerState(HardwareState.PowerState.OFF);
        log("Питание выключено");
    }

    /**
     * Check and report hardware errors.
     */
    public List<FrError> checkHardwareErrors() {
        List<FrError> errors = new ArrayList<>();
        if (hardwareState.isCoverOpen()) {
            errors.add(FrError.COVER_OPEN);
            notifyError(FrError.COVER_OPEN, "Крышка корпуса открыта");
        }
        if (!hardwareState.isPaperPresent()) {
            errors.add(FrError.PAPER_END);
            notifyError(FrError.PAPER_END, "Бумага закончилась");
        }
        if (!hardwareState.isPaperRollPresent()) {
            errors.add(FrError.PAPER_ABSENT);
            notifyError(FrError.PAPER_ABSENT, "Рулон бумаги отсутствует");
        }
        if (!hardwareState.isFnConnected()) {
            errors.add(FrError.FN_ERROR);
            notifyError(FrError.FN_ERROR, "ФН не подключен");
        }
        return errors;
    }

    /**
     * Open a shift session in the FN.
     */
    public boolean openSession() {
        if (!isReadyForOperation()) return false;
        FiscalAccumulator.FnResponse response = fiscalAccumulator.processCommand(
            FiscalAccumulator.CMD_OPEN_SESSION, null
        );
        if (response.isOk()) {
            log("Смена №" + fiscalAccumulator.getSessionNumber() + " открыта");
            printSessionHeader("ОТКРЫТИЕ СМЕНЫ");
            return true;
        } else {
            notifyError(FrError.FN_ERROR, "Ошибка открытия смены: 0x"
                + String.format("%02X", response.errorCode));
            return false;
        }
    }

    /**
     * Close a shift session in the FN.
     */
    public boolean closeSession() {
        if (!isReadyForOperation()) return false;
        FiscalAccumulator.FnResponse response = fiscalAccumulator.processCommand(
            FiscalAccumulator.CMD_CLOSE_SESSION, null
        );
        if (response.isOk()) {
            log("Смена №" + fiscalAccumulator.getSessionNumber() + " закрыта");
            printSessionHeader("ЗАКРЫТИЕ СМЕНЫ");
            return true;
        } else {
            notifyError(FrError.FN_ERROR, "Ошибка закрытия смены: 0x"
                + String.format("%02X", response.errorCode));
            return false;
        }
    }

    /**
     * Print a test receipt for firmware testing purposes.
     */
    public boolean printTestReceipt() {
        if (!isReadyForOperation()) return false;

        // Open receipt (sale type = 0x01)
        FiscalAccumulator.FnResponse openResp = fiscalAccumulator.processCommand(
            FiscalAccumulator.CMD_OPEN_RECEIPT, new byte[]{0x01}
        );
        if (!openResp.isOk()) {
            notifyError(FrError.FN_ERROR, "Ошибка открытия чека");
            return false;
        }

        // Add item: price = 10000 kopecks (100 rub), qty = 1000 (1 unit)
        byte[] item1 = {(byte)0x27, (byte)0x10, (byte)0x03, (byte)0xE8};
        fiscalAccumulator.processCommand(FiscalAccumulator.CMD_ADD_ITEM, item1);

        // Add item: price = 5050 kopecks (50.50 rub), qty = 2000 (2 units)
        byte[] item2 = {(byte)0x13, (byte)0xBA, (byte)0x07, (byte)0xD0};
        fiscalAccumulator.processCommand(FiscalAccumulator.CMD_ADD_ITEM, item2);

        // Close receipt with total 20100 kopecks (201 rub)
        byte[] total = {(byte)0x00, (byte)0x00, (byte)0x4E, (byte)0xA4};
        FiscalAccumulator.FnResponse closeResp = fiscalAccumulator.processCommand(
            FiscalAccumulator.CMD_CLOSE_RECEIPT, total
        );

        if (closeResp.isOk()) {
            String receiptText = new String(closeResp.data);
            printerEmulator.print(receiptText);
            notifyReceiptPrinted(receiptText);
            return true;
        } else {
            notifyError(FrError.FN_ERROR, "Ошибка закрытия чека");
            return false;
        }
    }

    private void printSessionHeader(String title) {
        String text = String.join("\n",
            "================================",
            "         " + title,
            "ФН: " + fiscalAccumulator.getFnNumber(),
            "Смена: " + fiscalAccumulator.getSessionNumber(),
            "Документ: " + fiscalAccumulator.getDocumentNumber(),
            "================================"
        );
        printerEmulator.print(text);
        notifyReceiptPrinted(text);
    }

    private boolean isReadyForOperation() {
        if (!hardwareState.isPoweredOn()) {
            notifyError(FrError.POWERED_OFF, "Устройство выключено");
            return false;
        }
        if (!hardwareState.isFirmwareLoaded()) {
            notifyError(FrError.FIRMWARE_NOT_LOADED, "Прошивка не загружена");
            return false;
        }
        List<FrError> errors = new ArrayList<>();
        if (hardwareState.isCoverOpen()) {
            errors.add(FrError.COVER_OPEN);
        }
        if (!errors.isEmpty()) {
            notifyError(errors.get(0), "Устройство не готово к работе");
            return false;
        }
        return true;
    }

    private void notifyReceiptPrinted(String receiptText) {
        for (FrListener listener : listeners) {
            listener.onReceiptPrinted(receiptText);
        }
    }

    private void notifyError(FrError error, String message) {
        for (FrListener listener : listeners) {
            listener.onError(error, message);
        }
    }

    private void log(String message) {
        for (FrListener listener : listeners) {
            listener.onLogMessage(message);
        }
    }

    public HardwareState getHardwareState() { return hardwareState; }
    public FiscalAccumulator getFiscalAccumulator() { return fiscalAccumulator; }
    public PrinterEmulator getPrinterEmulator() { return printerEmulator; }
}
