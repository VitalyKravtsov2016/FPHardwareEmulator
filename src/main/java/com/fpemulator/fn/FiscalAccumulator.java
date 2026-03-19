package com.fpemulator.fn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Fiscal Accumulator (ФН – Фискальный Накопитель) emulation.
 *
 * The FN is a tamper-proof device connected to the KKT (cash register) via
 * a serial interface.  It stores all fiscal documents and generates
 * cryptographic fiscal signatures (ФП).
 *
 * Command protocol (simplified, based on FZ-54 / ФНС RF):
 *   Each command is a byte sequence: [STX] [LEN_LO] [LEN_HI] [CMD] [DATA…] [CRC_LO] [CRC_HI]
 *   Response: [STX] [LEN_LO] [LEN_HI] [CMD] [ERROR] [DATA…] [CRC_LO] [CRC_HI]
 *
 * This class provides a higher-level Java API and also exposes a byte-level
 * command/response interface used by the UART bridge.
 */
public class FiscalAccumulator {

    private static final Logger LOG = Logger.getLogger(FiscalAccumulator.class.getName());

    // ── State machine ─────────────────────────────────────────────────────────

    public enum State {
        INITIAL,       // FN just registered / never activated
        OPEN_SHIFT,    // Shift is open (смена открыта)
        CLOSED_SHIFT,  // Shift is closed (смена закрыта / не открыта)
        IN_RECEIPT,    // A receipt is being formed
        EXPIRED        // FN storage is full (36 months / 250 000 docs)
    }

    // ── FN identification ─────────────────────────────────────────────────────

    private static final String FN_MODEL      = "ФН-1.1";
    private static final String FN_SERIAL_TMP = "9999078900006341";
    private final String fnSerial;
    private final String kktRegNumber;
    private final String inn;
    private final String shopName;
    private final String address;

    // ── Counters ──────────────────────────────────────────────────────────────

    private long totalDocCount  = 0;
    private long shiftNumber    = 0;
    private long receiptNumber  = 0;   // within current shift
    private long globalReceiptNumber = 0;

    // ── Current state ─────────────────────────────────────────────────────────

    private State state = State.CLOSED_SHIFT;
    private FiscalRecord currentReceipt = null;

    // ── Storage ───────────────────────────────────────────────────────────────

    private final List<FiscalRecord> records = new ArrayList<>();

    // ── Listeners ─────────────────────────────────────────────────────────────

    private Consumer<FiscalRecord> receiptClosedListener;

    // ── Random for fiscal sign generation ─────────────────────────────────────

    private final Random random = new Random();

    public FiscalAccumulator(String kktRegNumber, String inn, String shopName, String address) {
        this.fnSerial       = FN_SERIAL_TMP;
        this.kktRegNumber   = kktRegNumber;
        this.inn            = inn;
        this.shopName       = shopName;
        this.address        = address;
    }

    // ── Shift management ─────────────────────────────────────────────────────

    public synchronized void openShift() {
        if (state == State.IN_RECEIPT) {
            throw new IllegalStateException("Cannot open shift while receipt is open");
        }
        if (state == State.OPEN_SHIFT) {
            LOG.warning("Shift already open");
            return;
        }
        shiftNumber++;
        receiptNumber = 0;
        state = State.OPEN_SHIFT;
        totalDocCount++;
        LOG.info(String.format("Shift #%d opened", shiftNumber));
    }

    public synchronized FiscalRecord closeShift() {
        if (state == State.IN_RECEIPT) {
            throw new IllegalStateException("Cannot close shift while receipt is open");
        }
        if (state != State.OPEN_SHIFT) {
            LOG.warning("No open shift to close");
            return null;
        }
        state = State.CLOSED_SHIFT;
        totalDocCount++;
        LOG.info(String.format("Shift #%d closed. Receipts: %d", shiftNumber, receiptNumber));
        return null;
    }

    // ── Receipt management ────────────────────────────────────────────────────

    public synchronized void openReceipt(FiscalRecord.Type type) {
        if (state != State.OPEN_SHIFT) {
            throw new IllegalStateException("Shift must be open to create a receipt");
        }
        globalReceiptNumber++;
        receiptNumber++;
        currentReceipt = new FiscalRecord(globalReceiptNumber, shiftNumber, type);
        currentReceipt.setKktRegNumber(kktRegNumber);
        currentReceipt.setFnSerialNumber(fnSerial);
        state = State.IN_RECEIPT;
        LOG.info(String.format("Receipt #%d (shift #%d) opened", globalReceiptNumber, shiftNumber));
    }

    public synchronized void addItem(String name, int quantity, long price, int vatRate) {
        if (state != State.IN_RECEIPT || currentReceipt == null) {
            throw new IllegalStateException("No open receipt");
        }
        currentReceipt.addItem(new ReceiptItem(name, quantity, price, vatRate));
    }

    public synchronized FiscalRecord closeReceipt(long cash, long card) {
        if (state != State.IN_RECEIPT || currentReceipt == null) {
            throw new IllegalStateException("No open receipt");
        }
        currentReceipt.close(cash, card);
        currentReceipt.setFiscalDocNumber(String.valueOf(totalDocCount + 1));
        currentReceipt.setFiscalSign(generateFiscalSign());
        totalDocCount++;
        records.add(currentReceipt);
        FiscalRecord closed = currentReceipt;
        currentReceipt = null;
        state = State.OPEN_SHIFT;
        LOG.info(String.format("Receipt #%d closed. Total: %.2f", closed.getNumber(), closed.getTotalAmount() / 100.0));
        if (receiptClosedListener != null) receiptClosedListener.accept(closed);
        return closed;
    }

    public synchronized void cancelReceipt() {
        if (state != State.IN_RECEIPT) return;
        currentReceipt = null;
        receiptNumber = Math.max(0, receiptNumber - 1);
        globalReceiptNumber = Math.max(0, globalReceiptNumber - 1);
        state = State.OPEN_SHIFT;
        LOG.info("Receipt cancelled");
    }

    // ── Fiscal sign generation (simulation) ───────────────────────────────────

    private String generateFiscalSign() {
        return String.format("%010d", Math.abs(random.nextLong() % 10_000_000_000L));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public State              getState()          { return state; }
    public long               getShiftNumber()    { return shiftNumber; }
    public long               getReceiptNumber()  { return globalReceiptNumber; }
    public long               getTotalDocCount()  { return totalDocCount; }
    public String             getFnSerial()       { return fnSerial; }
    public String             getFnModel()        { return FN_MODEL; }
    public String             getKktRegNumber()   { return kktRegNumber; }
    public String             getInn()            { return inn; }
    public String             getShopName()       { return shopName; }
    public String             getAddress()        { return address; }
    public FiscalRecord       getCurrentReceipt() { return currentReceipt; }
    public List<FiscalRecord> getRecords()        { return Collections.unmodifiableList(records); }

    public void setReceiptClosedListener(Consumer<FiscalRecord> listener) {
        this.receiptClosedListener = listener;
    }

    /** Format last closed receipt as text, or a placeholder if none. */
    public String formatLastReceipt() {
        if (records.isEmpty()) return "(нет закрытых чеков)";
        FiscalRecord last = records.get(records.size() - 1);
        return last.format(shopName, inn, address);
    }
}
