package com.fpemulator.cpu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ARM Cortex-M3 core (CortexM3Core).
 *
 * Each test loads a small synthetic firmware image, resets the CPU,
 * executes instructions, and checks the resulting register state.
 */
class CortexM3CoreTest {

    private Memory        memory;
    private CortexM3Core  cpu;
    private Registers     regs;

    /** Flash base – instructions are placed here */
    private static final int FLASH  = Memory.FLASH_BASE;
    /** Stack top – well within SRAM to ensure SP-relative stores are valid */
    private static final int SP_TOP = Memory.SRAM_BASE + Memory.SRAM_SIZE - 64;

    @BeforeEach
    void setUp() {
        memory = new Memory();
        cpu    = new CortexM3Core(memory);
        regs   = cpu.getRegisters();
    }

    /**
     * Build a minimal vector table at Flash[0]:
     *   [0] = initial SP
     *   [4] = reset handler address | 1 (Thumb)
     * Then write instructions starting at {@code codeAddr}.
     */
    private void buildFirmware(int codeAddr, byte[] instructions) {
        byte[] flash = new byte[Memory.FLASH_SIZE];

        // Vector table
        writeWord(flash, 0, SP_TOP);
        writeWord(flash, 4, codeAddr | 1);

        // Code
        int offset = codeAddr - FLASH;
        System.arraycopy(instructions, 0, flash, offset, instructions.length);

        memory.loadFlash(flash, 0);
        cpu.reset();
    }

    private static void writeWord(byte[] buf, int offset, int value) {
        buf[offset]     = (byte)(value & 0xFF);
        buf[offset + 1] = (byte)((value >> 8) & 0xFF);
        buf[offset + 2] = (byte)((value >> 16) & 0xFF);
        buf[offset + 3] = (byte)((value >> 24) & 0xFF);
    }

    private static byte[] hw(int halfword) {
        return new byte[]{(byte)(halfword & 0xFF), (byte)((halfword >> 8) & 0xFF)};
    }

    // Concatenate byte arrays
    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }

    @Test
    void testResetLoadsSpAndPc() {
        buildFirmware(0x100, hw(0xBE00)); // BKPT #0 – halt immediately
        assertEquals(SP_TOP, regs.getSP());
        assertEquals(0x100,  regs.getPC());
    }

    @Test
    void testMovImmediate() {
        // MOVS R0, #42   (0x202A = 0010 0 000 00101010)
        byte[] code = concat(hw(0x202A), hw(0xBE00));
        buildFirmware(0x100, code);
        cpu.step(); // MOVS R0, #42
        assertEquals(42, regs.get(Registers.R0));
        assertFalse(regs.isN());
        assertFalse(regs.isZ());
    }

    @Test
    void testMovImmediateZero() {
        // MOVS R1, #0 (0x2100)
        byte[] code = concat(hw(0x2100), hw(0xBE00));
        buildFirmware(0x100, code);
        cpu.step();
        assertEquals(0, regs.get(Registers.R1));
        assertTrue(regs.isZ());
    }

    @Test
    void testAddRegister() {
        // MOVS R0, #10   (0x200A)
        // MOVS R1, #20   (0x2114)
        // ADDS R2, R0, R1  = 0001_100_001_000_010 = 0x1842
        byte[] code = concat(hw(0x200A), hw(0x2114), hw(0x1842), hw(0xBE00));
        buildFirmware(0x100, code);
        cpu.step(); // MOVS R0, #10
        cpu.step(); // MOVS R1, #20
        cpu.step(); // ADDS R2, R0, R1
        assertEquals(30, regs.get(Registers.R2));
    }

    @Test
    void testSubImmediate() {
        // MOVS R0, #100 (0x2064)
        // SUBS R0, #30  (0x381E = 0011 1 000 00011110)
        byte[] code = concat(hw(0x2064), hw(0x381E), hw(0xBE00));
        buildFirmware(0x100, code);
        cpu.step(); // MOVS R0, #100
        cpu.step(); // SUBS R0, #30
        assertEquals(70, regs.get(Registers.R0));
    }

    @Test
    void testBreakpointHalts() {
        byte[] code = hw(0xBE00); // BKPT #0
        buildFirmware(0x100, code);
        boolean ok = cpu.step(); // hits BKPT
        assertFalse(ok);
        assertTrue(cpu.isHalted());
    }

    @Test
    void testProgrammaticBreakpoint() {
        // MOVS R0, #7  (0x2007)
        // MOVS R1, #8  (0x2108)
        byte[] code = concat(hw(0x2007), hw(0x2108), hw(0xBE00));
        buildFirmware(0x100, code);
        cpu.addBreakpoint(0x102); // break before second instruction
        cpu.step(); // executes MOVS R0, #7
        // Next step should hit breakpoint
        boolean ok = cpu.step();
        assertFalse(ok);
        assertEquals(7, regs.get(Registers.R0));
        // R1 not set yet
        assertEquals(0, regs.get(Registers.R1));
    }

    @Test
    void testStorLoadWord() {
        // MOVS R0, #0xAB = 171   (0x20AB)
        // STR  R0, [SP, #0]      (0x9000)
        // LDR  R1, [SP, #0]      (0x9900)
        byte[] code = concat(hw(0x20AB), hw(0x9000), hw(0x9900), hw(0xBE00));
        buildFirmware(0x100, code);
        cpu.step(); // MOVS R0
        cpu.step(); // STR
        cpu.step(); // LDR R1
        assertEquals(0xAB, regs.get(Registers.R1));
    }
}
