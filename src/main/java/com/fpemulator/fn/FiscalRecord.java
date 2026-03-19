package com.fpemulator.fn;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fiscal receipt (чек).
 *
 * Types:
 *   SALE      – приход
 *   RETURN    – возврат прихода
 *   EXPENSE   – расход
 *   EXPENSE_RETURN – возврат расхода
 */
public class FiscalRecord {

    public enum Type {
        SALE("Приход"), RETURN("Возврат прихода"),
        EXPENSE("Расход"), EXPENSE_RETURN("Возврат расхода");

        private final String label;
        Type(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final long          number;          // global receipt counter
    private final long          shiftNumber;     // shift number
    private final LocalDateTime dateTime;
    private final Type          type;
    private final List<ReceiptItem> items = new ArrayList<>();

    // Totals in kopecks
    private long totalAmount   = 0;
    private long cashAmount    = 0;
    private long cardAmount    = 0;
    private long changeAmount  = 0;

    // KKT / FN identifiers (populated by FiscalAccumulator)
    private String kktRegNumber = "";
    private String fnSerialNumber = "";
    private String fiscalDocNumber = "";
    private String fiscalSign = "";

    private boolean closed = false;

    public FiscalRecord(long number, long shiftNumber, Type type) {
        this.number      = number;
        this.shiftNumber = shiftNumber;
        this.type        = type;
        this.dateTime    = LocalDateTime.now();
    }

    public void addItem(ReceiptItem item) {
        if (closed) throw new IllegalStateException("Receipt is already closed");
        items.add(item);
        totalAmount += item.getTotal();
    }

    public void close(long cash, long card) {
        this.cashAmount   = cash;
        this.cardAmount   = card;
        this.changeAmount = Math.max(0, cash + card - totalAmount);
        this.closed = true;
    }

    // ── Formatted receipt text ────────────────────────────────────────────────

    /** Generate a text receipt (чек) suitable for display and printing. */
    public String format(String shopName, String inn, String address) {
        StringBuilder sb = new StringBuilder();
        String line = "=".repeat(44);
        String thinLine = "-".repeat(44);

        sb.append(line).append("\n");
        sb.append(center(shopName, 44)).append("\n");
        sb.append(center("ИНН " + inn, 44)).append("\n");
        sb.append(center(address, 44)).append("\n");
        sb.append(thinLine).append("\n");
        sb.append(String.format("%-22s%22s\n",
                "Дата: " + dateTime.format(DATE_FMT),
                "Время: " + dateTime.format(TIME_FMT)));
        sb.append(String.format("Смена: %-10d  Чек: %-10d\n", shiftNumber, number));
        sb.append(center(type.getLabel(), 44)).append("\n");
        sb.append(thinLine).append("\n");

        for (ReceiptItem item : items) {
            sb.append(item.getName()).append("\n");
            sb.append(String.format("  %6.3f x %8.2f = %10.2f руб.\n",
                    item.getQuantity() / 1000.0,
                    item.getPrice() / 100.0,
                    item.getTotal() / 100.0));
        }

        sb.append(thinLine).append("\n");
        sb.append(String.format("%-30s%14.2f руб.\n", "ИТОГО:", totalAmount / 100.0));
        if (cashAmount > 0)
            sb.append(String.format("%-30s%14.2f руб.\n", "Наличными:", cashAmount / 100.0));
        if (cardAmount > 0)
            sb.append(String.format("%-30s%14.2f руб.\n", "Картой:", cardAmount / 100.0));
        if (changeAmount > 0)
            sb.append(String.format("%-30s%14.2f руб.\n", "Сдача:", changeAmount / 100.0));

        sb.append(thinLine).append("\n");
        sb.append(String.format("РН ККТ: %-36s\n", kktRegNumber));
        sb.append(String.format("ФН: %-40s\n", fnSerialNumber));
        sb.append(String.format("ФД: %-40s\n", fiscalDocNumber));
        sb.append(String.format("ФП: %-40s\n", fiscalSign));
        sb.append(line).append("\n");
        sb.append(center("Спасибо за покупку!", 44)).append("\n");

        return sb.toString();
    }

    private static String center(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int pad = (width - s.length()) / 2;
        return " ".repeat(pad) + s;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long          getNumber()       { return number; }
    public long          getShiftNumber()  { return shiftNumber; }
    public LocalDateTime getDateTime()     { return dateTime; }
    public Type          getType()         { return type; }
    public List<ReceiptItem> getItems()    { return Collections.unmodifiableList(items); }
    public long          getTotalAmount()  { return totalAmount; }
    public long          getCashAmount()   { return cashAmount; }
    public long          getCardAmount()   { return cardAmount; }
    public long          getChangeAmount() { return changeAmount; }
    public boolean       isClosed()        { return closed; }

    public String getFiscalDocNumber() { return fiscalDocNumber; }
    public String getFiscalSign()      { return fiscalSign; }
    public String getKktRegNumber()    { return kktRegNumber; }
    public String getFnSerialNumber()  { return fnSerialNumber; }

    public void setKktRegNumber(String v)    { kktRegNumber = v; }
    public void setFnSerialNumber(String v)  { fnSerialNumber = v; }
    public void setFiscalDocNumber(String v) { fiscalDocNumber = v; }
    public void setFiscalSign(String v)      { fiscalSign = v; }
}
