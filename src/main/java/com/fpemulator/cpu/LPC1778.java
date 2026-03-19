package com.fpemulator.cpu;

import com.fpemulator.peripheral.PeripheralBus;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Top-level LPC1778 microcontroller model.
 *
 * Integrates:
 *   – CortexM3Core (CPU)
 *   – Memory (Flash + SRAM)
 *   – PeripheralBus (all on-chip peripherals)
 *
 * Usage:
 *   LPC1778 mcu = new LPC1778();
 *   mcu.loadFirmware(bytes);
 *   mcu.reset();
 *   mcu.start();  // runs CPU in background thread
 *   …
 *   mcu.stop();
 */
public class LPC1778 {

    private static final Logger LOG = Logger.getLogger(LPC1778.class.getName());

    private final Memory         memory;
    private final CortexM3Core   cpu;
    private final PeripheralBus  peripherals;

    private Thread cpuThread;
    private volatile boolean firmwareLoaded = false;

    public LPC1778() {
        memory      = new Memory();
        peripherals = new PeripheralBus(memory);
        cpu         = new CortexM3Core(memory);

        // Wire peripheral read/write callbacks into memory
        memory.setPeripheralCallbacks(peripherals::read, peripherals::write);
    }

    // ── Firmware loading ──────────────────────────────────────────────────────

    /** Load a raw binary firmware image (.bin) into Flash. */
    public void loadFirmware(byte[] firmwareBytes) {
        memory.loadFlash(firmwareBytes, 0);
        firmwareLoaded = true;
        LOG.info(String.format("Firmware loaded: %d bytes", firmwareBytes.length));
    }

    /** Load firmware from an InputStream (e.g. from a file chooser). */
    public void loadFirmware(InputStream is) throws IOException {
        byte[] data = is.readAllBytes();
        loadFirmware(data);
    }

    public boolean isFirmwareLoaded() { return firmwareLoaded; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Reset the MCU: zero SRAM, reset peripherals, reset CPU. */
    public void reset() {
        memory.resetSRAM();
        peripherals.reset();
        cpu.reset();
        LOG.info("LPC1778 reset");
    }

    /** Start the CPU in a background thread. */
    public synchronized void start() {
        if (cpuThread != null && cpuThread.isAlive()) {
            LOG.warning("CPU already running");
            return;
        }
        cpuThread = new Thread(() -> {
            LOG.info("CPU thread started");
            cpu.run();
            LOG.info("CPU thread stopped");
        }, "lpc1778-cpu");
        cpuThread.setDaemon(true);
        cpuThread.start();
    }

    /** Stop the CPU thread. */
    public synchronized void stop() {
        cpu.stop();
        if (cpuThread != null) {
            cpuThread.interrupt();
            cpuThread = null;
        }
        LOG.info("LPC1778 stopped");
    }

    /** Execute a single instruction (for step-by-step debugging). */
    public boolean step() {
        return cpu.step();
    }

    public boolean isRunning() { return cpuThread != null && cpuThread.isAlive() && cpu.isRunning(); }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public CortexM3Core  getCpu()         { return cpu; }
    public Memory        getMemory()      { return memory; }
    public PeripheralBus getPeripherals() { return peripherals; }
}
