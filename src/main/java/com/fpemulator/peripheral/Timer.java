package com.fpemulator.peripheral;

/**
 * General-purpose timer emulation (TIM0–TIM3) for LPC1778.
 *
 * Each timer has:
 *   TCR  – Timer Control Register
 *   TC   – Timer Counter
 *   PR   – Prescale Register
 *   PC   – Prescale Counter
 *   MR0  – Match Register 0
 *   MCR  – Match Control Register (reset/interrupt on match)
 *   IR   – Interrupt Register
 *
 * The timer is incremented by calling tick(cycles) from the emulation loop.
 */
public class Timer {

    private final int baseAddress;
    private final int index;

    // Registers
    private int ir;   // Interrupt Register
    private int tcr;  // Timer Control
    private int tc;   // Timer Counter
    private int pr;   // Prescale
    private int pc;   // Prescale Counter
    private int mcr;  // Match Control
    private int mr0;  // Match Register 0
    private int mr1;
    private int mr2;
    private int mr3;

    // IRQ callback
    private Runnable irqCallback;

    public Timer(int index, int baseAddress) {
        this.index       = index;
        this.baseAddress = baseAddress;
    }

    public void reset() {
        ir = tc = pc = 0;
        tcr = 0;
        pr  = 0;
        mcr = 0;
        mr0 = mr1 = mr2 = mr3 = 0;
    }

    /** Advance the timer by the given number of peripheral clock cycles. */
    public void tick(int cycles) {
        if ((tcr & 0x1) == 0) return; // timer disabled
        for (int i = 0; i < cycles; i++) {
            pc++;
            if (pc > pr) {
                pc = 0;
                tc++;
                checkMatch();
            }
        }
    }

    private void checkMatch() {
        if (tc == mr0) {
            ir |= 0x1;
            if ((mcr & 0x2) != 0) tc = 0;       // reset on match
            if ((mcr & 0x1) != 0 && irqCallback != null) irqCallback.run();
        }
    }

    public int read(int address) {
        int offset = address - baseAddress;
        return switch (offset) {
            case 0x00 -> ir;
            case 0x04 -> tcr;
            case 0x08 -> tc;
            case 0x0C -> pr;
            case 0x10 -> pc;
            case 0x14 -> mcr;
            case 0x18 -> mr0;
            case 0x1C -> mr1;
            case 0x20 -> mr2;
            case 0x24 -> mr3;
            default   -> 0;
        };
    }

    public void write(int address, int value) {
        int offset = address - baseAddress;
        switch (offset) {
            case 0x00 -> ir  &= ~value;   // write 1 to clear
            case 0x04 -> { tcr = value; if ((tcr & 0x2) != 0) { tc = 0; pc = 0; } } // reset bit
            case 0x08 -> tc  = value;
            case 0x0C -> pr  = value;
            case 0x10 -> pc  = value;
            case 0x14 -> mcr = value;
            case 0x18 -> mr0 = value;
            case 0x1C -> mr1 = value;
            case 0x20 -> mr2 = value;
            case 0x24 -> mr3 = value;
        }
    }

    public void setIrqCallback(Runnable cb) { this.irqCallback = cb; }
    public int getBaseAddress() { return baseAddress; }
    public int getIndex()       { return index; }
}
