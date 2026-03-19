package com.fpemulator.cpu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Registers model.
 */
class RegistersTest {

    private Registers regs;

    @BeforeEach
    void setUp() {
        regs = new Registers();
    }

    @Test
    void testReset() {
        regs.set(Registers.R0, 0xDEAD);
        regs.setPC(0x1000);
        regs.reset();
        assertEquals(0, regs.get(Registers.R0));
        assertEquals(0, regs.getPC());
    }

    @Test
    void testGetSet() {
        for (int i = 0; i < 16; i++) {
            regs.set(i, i * 0x1000);
            assertEquals(i * 0x1000, regs.get(i));
        }
    }

    @Test
    void testPCThumbBitCleared() {
        // Setting PC with Thumb bit set should clear the LSB in the stored value
        regs.setPC(0x1001);
        assertEquals(0x1000, regs.getPC());
    }

    @Test
    void testNZFlags() {
        regs.updateNZ(0);
        assertTrue(regs.isZ());
        assertFalse(regs.isN());

        regs.updateNZ(-1);
        assertTrue(regs.isN());
        assertFalse(regs.isZ());

        regs.updateNZ(1);
        assertFalse(regs.isN());
        assertFalse(regs.isZ());
    }

    @Test
    void testAddFlags() {
        // 0x7FFFFFFF + 1 → overflow and no carry (signed overflow)
        regs.updateNZCV_Add(0x7FFFFFFF, 1, 0x80000000L);
        assertTrue(regs.isV());   // signed overflow
        assertFalse(regs.isC());  // no unsigned carry
        assertTrue(regs.isN());   // result is negative

        // 0xFFFFFFFF + 1 → carry, zero
        regs.updateNZCV_Add(0xFFFFFFFF, 1, 0x100000000L);
        assertTrue(regs.isC());
        assertTrue(regs.isZ());
    }

    @Test
    void testSubFlags() {
        // 5 - 3 = 2, no borrow, no overflow
        regs.updateNZCV_Sub(5, 3);
        assertFalse(regs.isN());
        assertFalse(regs.isZ());
        assertTrue(regs.isC());   // ARM: C=1 means no borrow
        assertFalse(regs.isV());

        // 3 - 5 = -2, borrow, no overflow
        regs.updateNZCV_Sub(3, 5);
        assertTrue(regs.isN());
        assertFalse(regs.isZ());
        assertFalse(regs.isC()); // borrow
    }
}
