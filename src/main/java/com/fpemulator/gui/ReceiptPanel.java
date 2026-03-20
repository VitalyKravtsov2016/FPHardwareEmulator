package com.fpemulator.gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Panel that displays the printed receipt output from the fiscal register.
 */
public class ReceiptPanel extends JPanel {

    private final JTextArea receiptArea;
    private final JScrollPane scrollPane;
    private final JButton clearButton;

    private static final Font RECEIPT_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Color RECEIPT_BG = new Color(255, 255, 240);
    private static final Color RECEIPT_FG = Color.BLACK;

    public ReceiptPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Окно чека",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));

        receiptArea = new JTextArea();
        receiptArea.setFont(RECEIPT_FONT);
        receiptArea.setBackground(RECEIPT_BG);
        receiptArea.setForeground(RECEIPT_FG);
        receiptArea.setEditable(false);
        receiptArea.setLineWrap(false);
        receiptArea.setMargin(new Insets(8, 8, 8, 8));

        scrollPane = new JScrollPane(receiptArea);
        scrollPane.setPreferredSize(new Dimension(320, 400));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        clearButton = new JButton("Очистить");
        clearButton.addActionListener(e -> clearReceipt());

        add(scrollPane, BorderLayout.CENTER);
        add(clearButton, BorderLayout.SOUTH);
    }

    /**
     * Append a printed line to the receipt window.
     */
    public void appendLine(String line) {
        SwingUtilities.invokeLater(() -> {
            receiptArea.append(line + "\n");
            receiptArea.setCaretPosition(receiptArea.getDocument().getLength());
        });
    }

    /**
     * Append a full receipt text block.
     */
    public void appendReceipt(String receiptText) {
        SwingUtilities.invokeLater(() -> {
            receiptArea.append(receiptText + "\n");
            receiptArea.append("- - - - - - - - - - - - - - - -\n");
            receiptArea.setCaretPosition(receiptArea.getDocument().getLength());
        });
    }

    /**
     * Append a separator cut mark.
     */
    public void appendCut() {
        SwingUtilities.invokeLater(() -> {
            receiptArea.append("================================\n\n");
            receiptArea.setCaretPosition(receiptArea.getDocument().getLength());
        });
    }

    /**
     * Append a log/system message (shown differently from receipt output).
     */
    public void appendLogMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            receiptArea.append("[" + time + "] " + message + "\n");
            receiptArea.setCaretPosition(receiptArea.getDocument().getLength());
        });
    }

    /**
     * Append an error message.
     */
    public void appendError(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            receiptArea.append("[" + time + "] ОШИБКА: " + message + "\n");
            receiptArea.setCaretPosition(receiptArea.getDocument().getLength());
        });
    }

    public void clearReceipt() {
        SwingUtilities.invokeLater(() -> receiptArea.setText(""));
    }

    public String getReceiptText() {
        return receiptArea.getText();
    }
}
