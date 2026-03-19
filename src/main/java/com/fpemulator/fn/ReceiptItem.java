package com.fpemulator.fn;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single item in a fiscal receipt.
 */
public class ReceiptItem {
    private final String name;
    private final int    quantity;   // in 1/1000 units (e.g. 1000 = 1 piece)
    private final long   price;      // in kopecks (1/100 ruble)
    private final long   total;      // price * quantity / 1000, in kopecks
    private final int    vatRate;    // VAT rate code: 1=18%, 2=10%, 3=0%, 4=free

    public ReceiptItem(String name, int quantity, long price, int vatRate) {
        this.name     = name;
        this.quantity = quantity;
        this.price    = price;
        this.total    = (price * quantity) / 1000L;
        this.vatRate  = vatRate;
    }

    public String getName()     { return name; }
    public int    getQuantity() { return quantity; }
    public long   getPrice()    { return price; }
    public long   getTotal()    { return total; }
    public int    getVatRate()  { return vatRate; }

    /** VAT rate as a human-readable string. */
    public String getVatRateString() {
        return switch (vatRate) {
            case 1 -> "18%";
            case 2 -> "10%";
            case 3 -> "0%";
            case 4 -> "Без НДС";
            default -> "?";
        };
    }

    @Override
    public String toString() {
        return String.format("%-20s %5.3f x %8.2f = %8.2f руб. [НДС %s]",
                name,
                quantity / 1000.0,
                price / 100.0,
                total / 100.0,
                getVatRateString());
    }
}
