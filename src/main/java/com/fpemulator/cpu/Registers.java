package com.fpemulator.cpu;

/**
 * Represents the register file of the ARM Cortex-M3 processor.
 * R0-R12: General-purpose registers
 * R13 (SP): Stack Pointer
 * R14 (LR): Link Register
 * R15 (PC): Program Counter
 * xPSR: Combined Program Status Register (APSR + IPSR + EPSR)
 */
public class Registers {

    public static final int R0  = 0;
    public static final int R1  = 1;
    public static final int R2  = 2;
    public static final int R3  = 3;
    public static final int R4  = 4;
    public static final int R5  = 5;
    public static final int R6  = 6;
    public static final int R7  = 7;
    public static final int R8  = 8;
    public static final int R9  = 9;
    public static final int R10 = 10;
    public static final int R11 = 11;
    public static final int R12 = 12;
    public static final int SP  = 13;
    public static final int LR  = 14;
    public static final int PC  = 15;

    // xPSR flag bits
    public static final int XPSR_N = 1 << 31; // Negative
    public static final int XPSR_Z = 1 << 30; // Zero
    public static final int XPSR_C = 1 << 29; // Carry
    public static final int XPSR_V = 1 << 28; // Overflow
    public static final int XPSR_Q = 1 << 27; // Saturation

    private final int[] regs = new int[16];
    private int xpsr;

    public void reset() {
        for (int i = 0; i < 16; i++) {
            regs[i] = 0;
        }
        xpsr = 0;
    }

    public int get(int index) {
        return regs[index & 0xF];
    }

    public void set(int index, int value) {
        regs[index & 0xF] = value;
    }

    public int getPC() { return regs[PC]; }
    public void setPC(int value) { regs[PC] = value & ~1; } // clear Thumb bit in stored PC

    public int getSP() { return regs[SP]; }
    public void setSP(int value) { regs[SP] = value; }

    public int getLR() { return regs[LR]; }
    public void setLR(int value) { regs[LR] = value; }

    public int getXPSR() { return xpsr; }
    public void setXPSR(int value) { xpsr = value; }

    public boolean isN() { return (xpsr & XPSR_N) != 0; }
    public boolean isZ() { return (xpsr & XPSR_Z) != 0; }
    public boolean isC() { return (xpsr & XPSR_C) != 0; }
    public boolean isV() { return (xpsr & XPSR_V) != 0; }

    public void setN(boolean v) { xpsr = v ? (xpsr | XPSR_N) : (xpsr & ~XPSR_N); }
    public void setZ(boolean v) { xpsr = v ? (xpsr | XPSR_Z) : (xpsr & ~XPSR_Z); }
    public void setC(boolean v) { xpsr = v ? (xpsr | XPSR_C) : (xpsr & ~XPSR_C); }
    public void setV(boolean v) { xpsr = v ? (xpsr | XPSR_V) : (xpsr & ~XPSR_V); }

    /** Update N and Z flags based on result. */
    public void updateNZ(int result) {
        setN(result < 0);
        setZ(result == 0);
    }

    /** Update N, Z, C, V flags for addition. */
    public void updateNZCV_Add(int a, int b, long result32) {
        int result = (int) result32;
        setN(result < 0);
        setZ(result == 0);
        setC((result32 & 0x1_0000_0000L) != 0);
        setV(((a ^ result) & (b ^ result) & 0x8000_0000) != 0);
    }

    /** Update N, Z, C, V flags for subtraction (a - b). */
    public void updateNZCV_Sub(int a, int b) {
        long result32 = (long)(a & 0xFFFF_FFFFL) - (long)(b & 0xFFFF_FFFFL);
        int result = (int) result32;
        setN(result < 0);
        setZ(result == 0);
        setC(result32 >= 0); // borrow inverted
        setV(((a ^ b) & (a ^ result) & 0x8000_0000) != 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            sb.append(String.format("R%d=0x%08X  ", i, regs[i]));
            if ((i + 1) % 4 == 0) sb.append("\n");
        }
        sb.append(String.format("SP=0x%08X  LR=0x%08X  PC=0x%08X\n", regs[SP], regs[LR], regs[PC]));
        sb.append(String.format("xPSR=0x%08X  N=%b Z=%b C=%b V=%b", xpsr, isN(), isZ(), isC(), isV()));
        return sb.toString();
    }
}
