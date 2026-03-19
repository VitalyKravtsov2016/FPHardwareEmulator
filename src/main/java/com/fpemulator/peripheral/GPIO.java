package com.fpemulator.peripheral;

/**
 * GPIO port emulation for LPC1778.
 *
 * LPC1778 has GPIO0–GPIO5 (32 pins each).
 * Register map per port (base = 0x20098000 + port*0x20):
 *   +0x00  FIODIR  – direction (1=output)
 *   +0x04  FIOMASK – mask
 *   +0x08  FIOPIN  – pin value
 *   +0x0C  FIOSET  – set bits
 *   +0x10  FIOCLR  – clear bits
 */
public class GPIO {

    public static final int GPIO_BASE = 0x20098000;
    private static final int PORT_STRIDE = 0x20;
    private static final int NUM_PORTS   = 6;

    private final int[] dir  = new int[NUM_PORTS];
    private final int[] mask = new int[NUM_PORTS];
    private final int[] pin  = new int[NUM_PORTS];

    public void reset() {
        for (int i = 0; i < NUM_PORTS; i++) {
            dir[i]  = 0;
            mask[i] = 0;
            pin[i]  = 0;
        }
    }

    public int read(int address) {
        int offset = address - GPIO_BASE;
        int port   = offset / PORT_STRIDE;
        int reg    = offset % PORT_STRIDE;
        if (port < 0 || port >= NUM_PORTS) return 0;
        return switch (reg) {
            case 0x00 -> dir[port];
            case 0x04 -> mask[port];
            case 0x08 -> pin[port] & ~mask[port];
            default   -> 0;
        };
    }

    public void write(int address, int value) {
        int offset = address - GPIO_BASE;
        int port   = offset / PORT_STRIDE;
        int reg    = offset % PORT_STRIDE;
        if (port < 0 || port >= NUM_PORTS) return;
        switch (reg) {
            case 0x00 -> dir[port]  = value;
            case 0x04 -> mask[port] = value;
            case 0x08 -> pin[port]  = value;
            case 0x0C -> pin[port] |= (value & ~mask[port]);   // FIOSET
            case 0x10 -> pin[port] &= ~(value & ~mask[port]);  // FIOCLR
        }
    }

    /** Simulate an external signal on a specific pin. */
    public void setPinExternal(int port, int pinNum, boolean high) {
        if (high) pin[port] |=  (1 << pinNum);
        else      pin[port] &= ~(1 << pinNum);
    }

    public int getPinValue(int port) {
        return pin[port];
    }
}
