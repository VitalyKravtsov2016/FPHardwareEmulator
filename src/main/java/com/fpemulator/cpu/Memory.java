package com.fpemulator.cpu;

import java.util.Arrays;

/**
 * Flat memory model for the LPC1778.
 *
 * LPC1778 memory map (simplified):
 *   0x0000_0000 – 0x0007_FFFF  Flash (512 KB)
 *   0x1000_0000 – 0x1000_7FFF  Local SRAM (32 KB)
 *   0x2000_0000 – 0x2000_7FFF  AHB SRAM Bank 0 (32 KB)
 *   0x2000_8000 – 0x2000_FFFF  AHB SRAM Bank 1 (32 KB)
 *   0x4000_0000 – 0x5FFF_FFFF  APB peripherals
 *   0x5000_0000 – 0x501F_FFFF  AHB peripherals
 *   0xE000_0000 – 0xE00F_FFFF  Private Peripheral Bus (NVIC, SysTick …)
 *
 * The emulator maps Flash, SRAM, and a peripheral register space.
 */
public class Memory {

    // Flash: 512 KB starting at 0x00000000
    public static final int FLASH_BASE   = 0x0000_0000;
    public static final int FLASH_SIZE   = 512 * 1024;

    // Local SRAM: 32 KB at 0x10000000
    public static final int SRAM_BASE    = 0x1000_0000;
    public static final int SRAM_SIZE    = 32 * 1024;

    // AHB SRAM: 64 KB at 0x20000000
    public static final int AHB_SRAM_BASE = 0x2000_0000;
    public static final int AHB_SRAM_SIZE = 64 * 1024;

    private final byte[] flash;
    private final byte[] sram;
    private final byte[] ahbSram;

    // Peripheral register read/write callbacks
    private PeripheralReadCallback peripheralRead;
    private PeripheralWriteCallback peripheralWrite;

    @FunctionalInterface
    public interface PeripheralReadCallback {
        int read(int address, int size);
    }

    @FunctionalInterface
    public interface PeripheralWriteCallback {
        void write(int address, int value, int size);
    }

    public Memory() {
        flash   = new byte[FLASH_SIZE];
        sram    = new byte[SRAM_SIZE];
        ahbSram = new byte[AHB_SRAM_SIZE];
    }

    public void setPeripheralCallbacks(PeripheralReadCallback read, PeripheralWriteCallback write) {
        this.peripheralRead  = read;
        this.peripheralWrite = write;
    }

    /** Load raw binary image into Flash at the given offset. */
    public void loadFlash(byte[] data, int offset) {
        int len = Math.min(data.length, FLASH_SIZE - offset);
        System.arraycopy(data, 0, flash, offset, len);
    }

    public void resetSRAM() {
        Arrays.fill(sram, (byte) 0);
        Arrays.fill(ahbSram, (byte) 0);
    }

    // ── Read operations ──────────────────────────────────────────────────────

    public int readByte(int address) {
        return readRaw(address, 1) & 0xFF;
    }

    public int readHalfWord(int address) {
        return readRaw(address, 2) & 0xFFFF;
    }

    public int readWord(int address) {
        return readRaw(address, 4);
    }

    private int readRaw(int address, int size) {
        long addr = address & 0xFFFF_FFFFL;
        if (addr < FLASH_BASE + FLASH_SIZE) {
            return readFromArray(flash, (int)(addr - FLASH_BASE), size);
        }
        if (addr >= SRAM_BASE && addr < SRAM_BASE + SRAM_SIZE) {
            return readFromArray(sram, (int)(addr - SRAM_BASE), size);
        }
        if (addr >= AHB_SRAM_BASE && addr < AHB_SRAM_BASE + AHB_SRAM_SIZE) {
            return readFromArray(ahbSram, (int)(addr - AHB_SRAM_BASE), size);
        }
        if (peripheralRead != null) {
            return peripheralRead.read(address, size);
        }
        return 0;
    }

    private int readFromArray(byte[] arr, int offset, int size) {
        if (offset < 0 || offset + size > arr.length) return 0;
        return switch (size) {
            case 1 -> arr[offset] & 0xFF;
            case 2 -> (arr[offset] & 0xFF) | ((arr[offset + 1] & 0xFF) << 8);
            case 4 -> (arr[offset] & 0xFF)
                    | ((arr[offset + 1] & 0xFF) << 8)
                    | ((arr[offset + 2] & 0xFF) << 16)
                    | ((arr[offset + 3] & 0xFF) << 24);
            default -> 0;
        };
    }

    // ── Write operations ─────────────────────────────────────────────────────

    public void writeByte(int address, int value) {
        writeRaw(address, value, 1);
    }

    public void writeHalfWord(int address, int value) {
        writeRaw(address, value, 2);
    }

    public void writeWord(int address, int value) {
        writeRaw(address, value, 4);
    }

    private void writeRaw(int address, int value, int size) {
        long addr = address & 0xFFFF_FFFFL;
        if (addr >= SRAM_BASE && addr < SRAM_BASE + SRAM_SIZE) {
            writeToArray(sram, (int)(addr - SRAM_BASE), value, size);
            return;
        }
        if (addr >= AHB_SRAM_BASE && addr < AHB_SRAM_BASE + AHB_SRAM_SIZE) {
            writeToArray(ahbSram, (int)(addr - AHB_SRAM_BASE), value, size);
            return;
        }
        if (peripheralWrite != null) {
            peripheralWrite.write(address, value, size);
        }
    }

    private void writeToArray(byte[] arr, int offset, int value, int size) {
        if (offset < 0 || offset + size > arr.length) return;
        switch (size) {
            case 1 -> arr[offset] = (byte)(value & 0xFF);
            case 2 -> {
                arr[offset]     = (byte)(value & 0xFF);
                arr[offset + 1] = (byte)((value >> 8) & 0xFF);
            }
            case 4 -> {
                arr[offset]     = (byte)(value & 0xFF);
                arr[offset + 1] = (byte)((value >> 8) & 0xFF);
                arr[offset + 2] = (byte)((value >> 16) & 0xFF);
                arr[offset + 3] = (byte)((value >> 24) & 0xFF);
            }
        }
    }

    /** Read a null-terminated ASCII string from memory for debugging. */
    public String readCString(int address) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            int b = readByte(address + i);
            if (b == 0) break;
            sb.append((char) b);
        }
        return sb.toString();
    }

    public byte[] getFlash() { return flash; }
}
