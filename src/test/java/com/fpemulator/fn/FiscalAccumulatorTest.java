package com.fpemulator.fn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the FiscalAccumulator.
 */
class FiscalAccumulatorTest {

    private FiscalAccumulator fn;

    @BeforeEach
    void setUp() {
        fn = new FiscalAccumulator("0000000001045673", "770100000021",
                "Тест магазин", "г. Москва");
    }

    @Test
    void testInitialState() {
        assertEquals(FiscalAccumulator.State.CLOSED_SHIFT, fn.getState());
        assertEquals(0, fn.getShiftNumber());
        assertEquals(0, fn.getReceiptNumber());
    }

    @Test
    void testOpenAndCloseShift() {
        fn.openShift();
        assertEquals(FiscalAccumulator.State.OPEN_SHIFT, fn.getState());
        assertEquals(1, fn.getShiftNumber());

        fn.closeShift();
        assertEquals(FiscalAccumulator.State.CLOSED_SHIFT, fn.getState());
    }

    @Test
    void testOpenReceiptWithoutOpenShiftThrows() {
        assertThrows(IllegalStateException.class,
                () -> fn.openReceipt(FiscalRecord.Type.SALE));
    }

    @Test
    void testFullReceiptLifecycle() {
        fn.openShift();
        fn.openReceipt(FiscalRecord.Type.SALE);
        assertEquals(FiscalAccumulator.State.IN_RECEIPT, fn.getState());
        assertNotNull(fn.getCurrentReceipt());

        fn.addItem("Хлеб", 1000, 5000, 2);   // 1 unit, 50.00 rub, VAT 10%
        fn.addItem("Молоко", 2000, 7500, 2);  // 2 units, 75.00 rub each

        FiscalRecord r = fn.closeReceipt(20000, 0); // pay 200 rub cash

        assertNotNull(r);
        assertTrue(r.isClosed());
        assertEquals(FiscalRecord.Type.SALE, r.getType());
        // Total: 50.00 + 2 * 75.00 = 200.00 rub → 20000 kopecks
        assertEquals(20000, r.getTotalAmount());
        assertEquals(20000, r.getCashAmount());
        assertEquals(0,     r.getChangeAmount()); // exact payment

        // FN should be back to OPEN_SHIFT
        assertEquals(FiscalAccumulator.State.OPEN_SHIFT, fn.getState());
        assertEquals(1, fn.getRecords().size());
    }

    @Test
    void testChangeCalculation() {
        fn.openShift();
        fn.openReceipt(FiscalRecord.Type.SALE);
        fn.addItem("Товар", 1000, 5000, 4); // 50.00 rub
        FiscalRecord r = fn.closeReceipt(10000, 0); // pay 100 rub
        assertEquals(5000, r.getChangeAmount()); // change 50.00 rub
    }

    @Test
    void testCancelReceipt() {
        fn.openShift();
        fn.openReceipt(FiscalRecord.Type.SALE);
        fn.addItem("Товар", 1000, 1000, 4);
        fn.cancelReceipt();
        assertEquals(FiscalAccumulator.State.OPEN_SHIFT, fn.getState());
        assertNull(fn.getCurrentReceipt());
        assertEquals(0, fn.getRecords().size());
    }

    @Test
    void testReceiptClosedListenerCalled() {
        boolean[] called = {false};
        fn.setReceiptClosedListener(r -> called[0] = true);
        fn.openShift();
        fn.openReceipt(FiscalRecord.Type.SALE);
        fn.addItem("Товар", 1000, 1000, 4);
        fn.closeReceipt(1000, 0);
        assertTrue(called[0]);
    }

    @Test
    void testMultipleShiftsAndReceipts() {
        fn.openShift();
        for (int i = 0; i < 3; i++) {
            fn.openReceipt(FiscalRecord.Type.SALE);
            fn.addItem("Позиция " + i, 1000, 1000, 4);
            fn.closeReceipt(1000, 0);
        }
        fn.closeShift();
        fn.openShift();
        assertEquals(2, fn.getShiftNumber());
        assertEquals(3, fn.getRecords().size());

        fn.openReceipt(FiscalRecord.Type.RETURN);
        fn.addItem("Возврат", 1000, 1000, 4);
        fn.closeReceipt(0, 0);
        assertEquals(4, fn.getRecords().size());
        assertEquals(FiscalRecord.Type.RETURN, fn.getRecords().get(3).getType());
    }

    @Test
    void testReceiptFormatContainsRequiredFields() {
        fn.openShift();
        fn.openReceipt(FiscalRecord.Type.SALE);
        fn.addItem("Хлеб", 1000, 5000, 2);
        FiscalRecord r = fn.closeReceipt(5000, 0);

        String formatted = r.format("Тест магазин", "770100000021", "г. Москва");
        assertTrue(formatted.contains("Тест магазин"));
        assertTrue(formatted.contains("770100000021"));
        assertTrue(formatted.contains("Хлеб"));
        assertTrue(formatted.contains("50,00") || formatted.contains("50.00"));
    }
}
