package com.fpemulator.peripheral;

import com.fpemulator.cpu.Memory;

import java.util.logging.Logger;

/**
 * Peripheral bus for the LPC1778.
 *
 * Routes memory-mapped I/O accesses to the correct peripheral model.
 *
 * LPC1778 peripheral base addresses used here:
 *   UART0  0x4000C000
 *   UART1  0x40010000
 *   UART2  0x40098000
 *   UART3  0x4009C000
 *   UART4  0x400A4000
 *   TIMER0 0x40004000
 *   TIMER1 0x40008000
 *   TIMER2 0x40090000
 *   TIMER3 0x40094000
 *   GPIO   0x20098000
 *   SC     0x400FC000  (System Control)
 */
public class PeripheralBus {

    private static final Logger LOG = Logger.getLogger(PeripheralBus.class.getName());

    // UART base addresses
    public static final int UART0_BASE = 0x4000C000;
    public static final int UART1_BASE = 0x40010000;
    public static final int UART2_BASE = 0x40098000;
    public static final int UART3_BASE = 0x4009C000;
    public static final int UART4_BASE = 0x400A4000;

    // Timer base addresses
    public static final int TIMER0_BASE = 0x40004000;
    public static final int TIMER1_BASE = 0x40008000;
    public static final int TIMER2_BASE = 0x40090000;
    public static final int TIMER3_BASE = 0x40094000;

    // GPIO base address
    public static final int GPIO_BASE_ADDR = GPIO.GPIO_BASE; // 0x20098000

    private static final int PERIPHERAL_REGION_SIZE = 0x100; // registers span at most 256 bytes

    private final UART[]  uarts  = new UART[5];
    private final Timer[] timers = new Timer[4];
    private final GPIO    gpio;

    // System Control registers (clock, power, pin config) – stub
    private final int[] scRegs = new int[0x200];

    public PeripheralBus(Memory memory) {
        uarts[0] = new UART(0, UART0_BASE);
        uarts[1] = new UART(1, UART1_BASE);
        uarts[2] = new UART(2, UART2_BASE);
        uarts[3] = new UART(3, UART3_BASE);
        uarts[4] = new UART(4, UART4_BASE);

        timers[0] = new Timer(0, TIMER0_BASE);
        timers[1] = new Timer(1, TIMER1_BASE);
        timers[2] = new Timer(2, TIMER2_BASE);
        timers[3] = new Timer(3, TIMER3_BASE);

        gpio = new GPIO();
    }

    public void reset() {
        for (UART u : uarts)   u.reset();
        for (Timer t : timers) t.reset();
        gpio.reset();
    }

    // ── Memory-mapped read ────────────────────────────────────────────────────

    public int read(int address, int size) {
        long addr = address & 0xFFFF_FFFFL;

        // UARTs
        for (UART u : uarts) {
            if (addr >= u.getBaseAddress() && addr < u.getBaseAddress() + PERIPHERAL_REGION_SIZE) {
                return u.read(address);
            }
        }

        // Timers
        for (Timer t : timers) {
            if (addr >= t.getBaseAddress() && addr < t.getBaseAddress() + PERIPHERAL_REGION_SIZE) {
                return t.read(address);
            }
        }

        // GPIO
        if (addr >= GPIO_BASE_ADDR && addr < GPIO_BASE_ADDR + 0x100) {
            return gpio.read(address);
        }

        // System Control (SC) – return stored value or default
        if (addr >= 0x400FC000L && addr < 0x400FC000L + scRegs.length * 4L) {
            int idx = (int)((addr - 0x400FC000L) / 4);
            return scRegs[idx];
        }

        LOG.finest(String.format("Unhandled peripheral read at 0x%08X", address));
        return 0;
    }

    // ── Memory-mapped write ───────────────────────────────────────────────────

    public void write(int address, int value, int size) {
        long addr = address & 0xFFFF_FFFFL;

        // UARTs
        for (UART u : uarts) {
            if (addr >= u.getBaseAddress() && addr < u.getBaseAddress() + PERIPHERAL_REGION_SIZE) {
                u.write(address, value);
                return;
            }
        }

        // Timers
        for (Timer t : timers) {
            if (addr >= t.getBaseAddress() && addr < t.getBaseAddress() + PERIPHERAL_REGION_SIZE) {
                t.write(address, value);
                return;
            }
        }

        // GPIO
        if (addr >= GPIO_BASE_ADDR && addr < GPIO_BASE_ADDR + 0x100) {
            gpio.write(address, value);
            return;
        }

        // System Control (SC)
        if (addr >= 0x400FC000L && addr < 0x400FC000L + scRegs.length * 4L) {
            int idx = (int)((addr - 0x400FC000L) / 4);
            scRegs[idx] = value;
            return;
        }

        LOG.finest(String.format("Unhandled peripheral write at 0x%08X = 0x%08X", address, value));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UART  getUART(int index)  { return uarts[index]; }
    public Timer getTimer(int index) { return timers[index]; }
    public GPIO  getGPIO()           { return gpio; }
}
