package com.fpemulator.ui;

import com.fpemulator.fn.FiscalRecord;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Panel that displays the current receipt (чек) and the history of
 * closed fiscal records.
 */
public class ReceiptPanel extends JPanel {

    private static final Font  MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Color PAPER_BG  = new Color(255, 255, 245);
    private static final Color PAPER_FG  = Color.BLACK;

    // Current / last receipt text area
    private final JTextArea receiptArea;
    // History list
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String>            historyList;
    private final JTextArea                historyDetail;

    // Stored closed records for detail view
    private final java.util.List<FiscalRecord> closedRecords = new java.util.ArrayList<>();

    private String shopName = "Магазин";
    private String inn      = "000000000000";
    private String address  = "";

    public ReceiptPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Чек / Фискальный накопитель",
                TitledBorder.LEFT, TitledBorder.TOP));

        // ── Left: current receipt ────────────────────────────────────────────
        receiptArea = new JTextArea(30, 46);
        receiptArea.setFont(MONO_FONT);
        receiptArea.setEditable(false);
        receiptArea.setBackground(PAPER_BG);
        receiptArea.setForeground(PAPER_FG);
        receiptArea.setLineWrap(false);
        JScrollPane receiptScroll = new JScrollPane(receiptArea);
        receiptScroll.setBorder(BorderFactory.createTitledBorder("Текущий чек"));
        receiptScroll.setPreferredSize(new Dimension(420, 500));

        // ── Right: history ───────────────────────────────────────────────────
        historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setFont(MONO_FONT.deriveFont(11f));
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(BorderFactory.createTitledBorder("История чеков"));
        historyScroll.setPreferredSize(new Dimension(240, 200));

        historyDetail = new JTextArea(15, 46);
        historyDetail.setFont(MONO_FONT);
        historyDetail.setEditable(false);
        historyDetail.setBackground(PAPER_BG);
        historyDetail.setForeground(PAPER_FG);
        JScrollPane detailScroll = new JScrollPane(historyDetail);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Детали"));

        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = historyList.getSelectedIndex();
                if (idx >= 0 && idx < closedRecords.size()) {
                    historyDetail.setText(closedRecords.get(idx).format(shopName, inn, address));
                    historyDetail.setCaretPosition(0);
                }
            }
        });

        JSplitPane historySplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyScroll, detailScroll);
        historySplit.setResizeWeight(0.3);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, receiptScroll, historySplit);
        mainSplit.setResizeWeight(0.6);

        add(mainSplit, BorderLayout.CENTER);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Update the current receipt display. */
    public void updateCurrentReceipt(String text) {
        SwingUtilities.invokeLater(() -> {
            receiptArea.setText(text);
            receiptArea.setCaretPosition(0);
        });
    }

    /** Called when a receipt has been closed; adds it to history. */
    public void addClosedReceipt(FiscalRecord record) {
        SwingUtilities.invokeLater(() -> {
            closedRecords.add(record);
            String label = String.format("#%05d  %s  %7.2f руб.  [%s]",
                    record.getNumber(),
                    record.getDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")),
                    record.getTotalAmount() / 100.0,
                    record.getType().getLabel());
            historyModel.addElement(label);
            historyList.setSelectedIndex(historyModel.size() - 1);
            receiptArea.setText(record.format(shopName, inn, address));
            receiptArea.setCaretPosition(0);
        });
    }

    /** Clear the current receipt display. */
    public void clearCurrentReceipt() {
        SwingUtilities.invokeLater(() -> receiptArea.setText(""));
    }

    public void setShopInfo(String shopName, String inn, String address) {
        this.shopName = shopName;
        this.inn      = inn;
        this.address  = address;
    }
}
