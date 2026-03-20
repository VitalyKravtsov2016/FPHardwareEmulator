package com.fpemulator.hardware;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the physical hardware state of the fiscal register.
 * All hardware flags that can be sensed or set by the firmware.
 */
public class HardwareState {

    public enum PowerState {
        OFF, ON
    }

    private PowerState powerState = PowerState.OFF;
    private boolean coverOpen = false;
    private boolean paperRollPresent = true;
    private boolean paperPresent = true;
    private boolean fnConnected = true;
    private byte[] firmware = null;
    private String firmwarePath = null;

    private final List<HardwareStateListener> listeners = new ArrayList<>();

    public interface HardwareStateListener {
        void onStateChanged(HardwareState state);
    }

    public void addListener(HardwareStateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HardwareStateListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (HardwareStateListener listener : listeners) {
            listener.onStateChanged(this);
        }
    }

    public PowerState getPowerState() {
        return powerState;
    }

    public void setPowerState(PowerState powerState) {
        this.powerState = powerState;
        notifyListeners();
    }

    public boolean isPoweredOn() {
        return powerState == PowerState.ON;
    }

    public boolean isCoverOpen() {
        return coverOpen;
    }

    public void setCoverOpen(boolean coverOpen) {
        this.coverOpen = coverOpen;
        notifyListeners();
    }

    public boolean isPaperRollPresent() {
        return paperRollPresent;
    }

    public void setPaperRollPresent(boolean paperRollPresent) {
        this.paperRollPresent = paperRollPresent;
        if (!paperRollPresent) {
            this.paperPresent = false;
        }
        notifyListeners();
    }

    public boolean isPaperPresent() {
        return paperPresent;
    }

    public void setPaperPresent(boolean paperPresent) {
        this.paperPresent = paperPresent;
        if (paperPresent && !paperRollPresent) {
            this.paperRollPresent = true;
        }
        notifyListeners();
    }

    public boolean isFnConnected() {
        return fnConnected;
    }

    public void setFnConnected(boolean fnConnected) {
        this.fnConnected = fnConnected;
        notifyListeners();
    }

    public byte[] getFirmware() {
        return firmware;
    }

    public void setFirmware(byte[] firmware) {
        this.firmware = firmware;
        notifyListeners();
    }

    public boolean isFirmwareLoaded() {
        return firmware != null && firmware.length > 0;
    }

    public String getFirmwarePath() {
        return firmwarePath;
    }

    public void setFirmwarePath(String firmwarePath) {
        this.firmwarePath = firmwarePath;
    }

    public int getFirmwareSize() {
        return firmware != null ? firmware.length : 0;
    }

    /**
     * Returns a status byte representing hardware error flags,
     * similar to what the firmware would read from hardware registers.
     * Bit 0: Cover open
     * Bit 1: Paper end
     * Bit 2: Paper roll absent
     * Bit 3: FN absent
     */
    public int getStatusByte() {
        int status = 0;
        if (coverOpen)         status |= 0x01;
        if (!paperPresent)     status |= 0x02;
        if (!paperRollPresent) status |= 0x04;
        if (!fnConnected)      status |= 0x08;
        return status;
    }

    @Override
    public String toString() {
        return String.format(
            "HardwareState{power=%s, cover=%s, paperRoll=%s, paper=%s, fn=%s, firmware=%s(%d bytes)}",
            powerState,
            coverOpen ? "OPEN" : "CLOSED",
            paperRollPresent ? "YES" : "NO",
            paperPresent ? "YES" : "NO",
            fnConnected ? "YES" : "NO",
            firmwarePath != null ? firmwarePath : "none",
            getFirmwareSize()
        );
    }
}
