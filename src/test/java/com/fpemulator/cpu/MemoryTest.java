package com.fpemulator.cpu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Memory model.
 */
class MemoryTest {

    private Memory memory;

    @BeforeEach
    void setUp() {
        memory = new Memory();
    }

    @Test
    void testFlashReadAfterLoad() {
        byte[] firmware = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        memory.loadFlash(firmware, 0);

        assertEquals(0x01, memory.readByte(0x00000000));
        assertEquals(0x0201, memory.readHalfWord(0x00000000));
        assertEquals(0x04030201, memory.readWord(0x00000000));
    }

    @Test
    void testSramReadWrite() {
        memory.writeWord(Memory.SRAM_BASE, 0xDEADBEEF);
        assertEquals(0xDEADBEEF, memory.readWord(Memory.SRAM_BASE));

        memory.writeByte(Memory.SRAM_BASE + 4, 0xAB);
        assertEquals(0xAB, memory.readByte(Memory.SRAM_BASE + 4));

        memory.writeHalfWord(Memory.SRAM_BASE + 8, 0x1234);
        assertEquals(0x1234, memory.readHalfWord(Memory.SRAM_BASE + 8));
    }

    @Test
    void testAhbSramReadWrite() {
        memory.writeWord(Memory.AHB_SRAM_BASE, 0xCAFEBABE);
        assertEquals(0xCAFEBABE, memory.readWord(Memory.AHB_SRAM_BASE));
    }

    @Test
    void testResetSram() {
        memory.writeWord(Memory.SRAM_BASE, 0x12345678);
        memory.writeWord(Memory.AHB_SRAM_BASE, 0xABCDEF01);
        memory.resetSRAM();
        assertEquals(0, memory.readWord(Memory.SRAM_BASE));
        assertEquals(0, memory.readWord(Memory.AHB_SRAM_BASE));
    }

    @Test
    void testPeripheralCallbackRead() {
        memory.setPeripheralCallbacks(
                (addr, size) -> 0xDEAD,
                (addr, val, size) -> {}
        );
        // Peripheral region: 0x40000000
        assertEquals(0xDEAD, memory.readWord(0x40000000));
    }

    @Test
    void testLittleEndianEncoding() {
        memory.writeWord(Memory.SRAM_BASE, 0x12345678);
        assertEquals(0x78, memory.readByte(Memory.SRAM_BASE));
        assertEquals(0x56, memory.readByte(Memory.SRAM_BASE + 1));
        assertEquals(0x34, memory.readByte(Memory.SRAM_BASE + 2));
        assertEquals(0x12, memory.readByte(Memory.SRAM_BASE + 3));
    }
}
