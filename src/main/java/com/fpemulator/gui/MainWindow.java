package com.fpemulator.gui;

import com.fpemulator.emulator.FiscalAccumulator;
import com.fpemulator.emulator.FiscalRegister;
import com.fpemulator.emulator.FirmwareLoader;
import com.fpemulator.hardware.HardwareState;
import com.fpemulator.printer.PrinterEmulator;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Main GUI window for the FP Hardware Emulator.
 * Provides controls for hardware state and displays the receipt output.
 */
public class MainWindow extends JFrame {

    // --- Models ---
    private final HardwareState hardwareState = new HardwareState();
    private final FiscalAccumulator fiscalAccumulator = new FiscalAccumulator();
    private final PrinterEmulator printerEmulator = new PrinterEmulator();
    private final FiscalRegister fiscalRegister = new FiscalRegister(
        hardwareState, fiscalAccumulator, printerEmulator
    );

    // --- Toolbar ---
    private JButton loadFirmwareButton;
    private JButton powerButton;
    private JButton openSessionButton;
    private JButton closeSessionButton;
    private JButton printTestButton;
    private JLabel firmwareLabel;

    // --- Hardware state toggles ---
    private JToggleButton coverToggle;
    private JToggleButton paperRollToggle;
    private JToggleButton paperToggle;
    private JToggleButton fnToggle;

    // --- Status ---
    private JLabel statusLabel;
    private JPanel statusIndicator;

    // --- Receipt ---
    private ReceiptPanel receiptPanel;

    // Colors
    private static final Color COLOR_ON      = new Color(0x2ECC71);
    private static final Color COLOR_OFF     = new Color(0xE74C3C);
    private static final Color COLOR_WARNING = new Color(0xF39C12);
    private static final Color COLOR_NEUTRAL = new Color(0xBDC3C7);

    public MainWindow() {
        super("FP Hardware Emulator — Эмулятор Фискального Регистратора");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));
        setMinimumSize(new Dimension(900, 600));

        buildUI();
        wireListeners();
        updateUIState();

        pack();
        setLocationRelativeTo(null);
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildControlPanel(), BorderLayout.WEST);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JToolBar buildToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        loadFirmwareButton = new JButton("Загрузить прошивку (.bin)");
        loadFirmwareButton.setIcon(UIManager.getIcon("FileView.fileIcon"));
        toolbar.add(loadFirmwareButton);

        toolbar.addSeparator();

        firmwareLabel = new JLabel("Прошивка не загружена");
        firmwareLabel.setForeground(Color.GRAY);
        firmwareLabel.setFont(firmwareLabel.getFont().deriveFont(Font.ITALIC));
        toolbar.add(firmwareLabel);

        toolbar.addSeparator();

        powerButton = new JButton("Включить");
        powerButton.setBackground(COLOR_OFF);
        powerButton.setOpaque(true);
        powerButton.setForeground(Color.WHITE);
        powerButton.setFont(powerButton.getFont().deriveFont(Font.BOLD));
        toolbar.add(powerButton);

        toolbar.addSeparator();

        openSessionButton = new JButton("Открыть смену");
        openSessionButton.setEnabled(false);
        toolbar.add(openSessionButton);

        closeSessionButton = new JButton("Закрыть смену");
        closeSessionButton.setEnabled(false);
        toolbar.add(closeSessionButton);

        toolbar.addSeparator();

        printTestButton = new JButton("Тестовый чек");
        printTestButton.setEnabled(false);
        toolbar.add(printTestButton);

        return toolbar;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Состояние оборудования",
            TitledBorder.LEFT,
            TitledBorder.TOP
        ));
        panel.setPreferredSize(new Dimension(220, 0));

        panel.add(Box.createVerticalStrut(10));

        // Power indicator
        JPanel powerRow = buildStatusRow("Питание:", buildLedIndicator(COLOR_OFF, "powerLed"));
        panel.add(powerRow);
        panel.add(Box.createVerticalStrut(8));

        // Cover
        coverToggle = new JToggleButton("Крышка ЗАКРЫТА");
        coverToggle.setSelected(false);
        styleToggle(coverToggle, false, "Крышка ЗАКРЫТА", "Крышка ОТКРЫТА");
        panel.add(buildToggleRow("Крышка корпуса:", coverToggle));
        panel.add(Box.createVerticalStrut(8));

        // Paper roll
        paperRollToggle = new JToggleButton("Рулон ЕСТЬ");
        paperRollToggle.setSelected(true);
        styleToggle(paperRollToggle, true, "Рулон ЕСТЬ", "Рулон ОТСУТСТВУЕТ");
        panel.add(buildToggleRow("Рулон бумаги:", paperRollToggle));
        panel.add(Box.createVerticalStrut(8));

        // Paper
        paperToggle = new JToggleButton("Бумага ЕСТЬ");
        paperToggle.setSelected(true);
        styleToggle(paperToggle, true, "Бумага ЕСТЬ", "Бумага ОТСУТСТВУЕТ");
        panel.add(buildToggleRow("Бумага:", paperToggle));
        panel.add(Box.createVerticalStrut(8));

        // FN
        fnToggle = new JToggleButton("ФН ПОДКЛЮЧЕН");
        fnToggle.setSelected(true);
        styleToggle(fnToggle, true, "ФН ПОДКЛЮЧЕН", "ФН ОТКЛЮЧЕН");
        panel.add(buildToggleRow("Фискальный накопитель:", fnToggle));
        panel.add(Box.createVerticalStrut(8));

        // Hardware status bits
        panel.add(Box.createVerticalStrut(12));
        JPanel hwStatusPanel = new JPanel(new BorderLayout());
        hwStatusPanel.setBorder(BorderFactory.createTitledBorder("Регистр статуса"));
        JLabel hwStatusLabel = new JLabel("0x00");
        hwStatusLabel.setName("hwStatusLabel");
        hwStatusLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        hwStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        hwStatusPanel.add(hwStatusLabel, BorderLayout.CENTER);
        panel.add(hwStatusPanel);

        panel.add(Box.createVerticalGlue());

        // FN info
        panel.add(Box.createVerticalStrut(8));
        JPanel fnInfoPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        fnInfoPanel.setBorder(BorderFactory.createTitledBorder("Информация ФН"));
        JLabel fnNumLabel = new JLabel("ФН: " + fiscalAccumulator.getFnNumber());
        fnNumLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        fnInfoPanel.add(fnNumLabel);
        panel.add(fnInfoPanel);

        return panel;
    }

    private JPanel buildStatusRow(String label, Component comp) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        row.add(new JLabel(label));
        row.add(comp);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return row;
    }

    private JPanel buildToggleRow(String label, JToggleButton toggle) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        row.add(new JLabel(label), BorderLayout.NORTH);
        row.add(toggle, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        return row;
    }

    private JPanel buildLedIndicator(Color color, String name) {
        JPanel led = new JPanel();
        led.setName(name);
        led.setPreferredSize(new Dimension(16, 16));
        led.setMaximumSize(new Dimension(16, 16));
        led.setBackground(color);
        led.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        led.setOpaque(true);
        return led;
    }

    private JPanel buildCenterPanel() {
        receiptPanel = new ReceiptPanel();
        receiptPanel.appendLogMessage("Эмулятор ФР запущен. Загрузите прошивку для начала работы.");
        return receiptPanel;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

        statusIndicator = new JPanel();
        statusIndicator.setPreferredSize(new Dimension(14, 14));
        statusIndicator.setBackground(COLOR_NEUTRAL);
        statusIndicator.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        statusLabel = new JLabel("Готов к работе");

        bar.add(statusIndicator, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    // -------------------------------------------------------------------------
    // Event wiring
    // -------------------------------------------------------------------------

    private void wireListeners() {
        loadFirmwareButton.addActionListener(e -> onLoadFirmware());
        powerButton.addActionListener(e -> onTogglePower());
        openSessionButton.addActionListener(e -> fiscalRegister.openSession());
        closeSessionButton.addActionListener(e -> fiscalRegister.closeSession());
        printTestButton.addActionListener(e -> fiscalRegister.printTestReceipt());

        coverToggle.addActionListener(e -> {
            boolean open = coverToggle.isSelected();
            hardwareState.setCoverOpen(open);
            styleToggle(coverToggle, !open, "Крышка ЗАКРЫТА", "Крышка ОТКРЫТА");
            receiptPanel.appendLogMessage("Крышка корпуса: " + (open ? "ОТКРЫТА" : "ЗАКРЫТА"));
            updateUIState();
        });

        paperRollToggle.addActionListener(e -> {
            boolean present = paperRollToggle.isSelected();
            hardwareState.setPaperRollPresent(present);
            styleToggle(paperRollToggle, present, "Рулон ЕСТЬ", "Рулон ОТСУТСТВУЕТ");
            // sync paper toggle
            if (!present) {
                paperToggle.setSelected(false);
                hardwareState.setPaperPresent(false);
                styleToggle(paperToggle, false, "Бумага ЕСТЬ", "Бумага ОТСУТСТВУЕТ");
            }
            receiptPanel.appendLogMessage("Рулон бумаги: " + (present ? "ЕСТЬ" : "ОТСУТСТВУЕТ"));
            updateUIState();
        });

        paperToggle.addActionListener(e -> {
            boolean present = paperToggle.isSelected();
            hardwareState.setPaperPresent(present);
            styleToggle(paperToggle, present, "Бумага ЕСТЬ", "Бумага ОТСУТСТВУЕТ");
            if (present && !paperRollToggle.isSelected()) {
                paperRollToggle.setSelected(true);
                hardwareState.setPaperRollPresent(true);
                styleToggle(paperRollToggle, true, "Рулон ЕСТЬ", "Рулон ОТСУТСТВУЕТ");
            }
            receiptPanel.appendLogMessage("Бумага: " + (present ? "ЕСТЬ" : "ОТСУТСТВУЕТ"));
            updateUIState();
        });

        fnToggle.addActionListener(e -> {
            boolean connected = fnToggle.isSelected();
            hardwareState.setFnConnected(connected);
            styleToggle(fnToggle, connected, "ФН ПОДКЛЮЧЕН", "ФН ОТКЛЮЧЕН");
            receiptPanel.appendLogMessage("ФН: " + (connected ? "подключен" : "отключен"));
            updateUIState();
        });

        // FiscalRegister events → receipt panel
        fiscalRegister.addListener(new FiscalRegister.FrListener() {
            @Override
            public void onReceiptPrinted(String receiptText) {
                receiptPanel.appendReceipt(receiptText);
                receiptPanel.appendCut();
            }

            @Override
            public void onError(FiscalRegister.FrError error, String message) {
                receiptPanel.appendError(message);
                setStatus(message, COLOR_WARNING);
            }

            @Override
            public void onLogMessage(String message) {
                receiptPanel.appendLogMessage(message);
                setStatus(message, COLOR_NEUTRAL);
            }
        });

        // Hardware state changes → update UI
        hardwareState.addListener(state -> SwingUtilities.invokeLater(this::updateUIState));
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void onLoadFirmware() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите файл прошивки (.bin)");
        chooser.setFileFilter(new FileNameExtensionFilter("Файлы прошивки (*.bin)", "bin"));
        chooser.setAcceptAllFileFilterUsed(true);

        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File selectedFile = chooser.getSelectedFile();
        try {
            FirmwareLoader.FirmwareInfo fw = FirmwareLoader.load(selectedFile.getAbsolutePath());
            hardwareState.setFirmware(fw.data);
            hardwareState.setFirmwarePath(selectedFile.getName());
            firmwareLabel.setText(selectedFile.getName() + " (" + fw.sizeBytes + " байт, " + fw.checksum + ")");
            firmwareLabel.setForeground(new Color(0x27AE60));
            firmwareLabel.setFont(firmwareLabel.getFont().deriveFont(Font.PLAIN));
            receiptPanel.appendLogMessage("Прошивка загружена: " + fw);
            setStatus("Прошивка загружена: " + selectedFile.getName(), COLOR_ON);
            updateUIState();
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                "Ошибка загрузки прошивки:\n" + ex.getMessage(),
                "Ошибка", JOptionPane.ERROR_MESSAGE);
            receiptPanel.appendError("Ошибка загрузки прошивки: " + ex.getMessage());
        }
    }

    private void onTogglePower() {
        if (!hardwareState.isPoweredOn()) {
            boolean ok = fiscalRegister.powerOn();
            if (!ok) return;
            powerButton.setText("Выключить");
            powerButton.setBackground(COLOR_ON);
            setStatus("Устройство включено", COLOR_ON);
        } else {
            fiscalRegister.powerOff();
            powerButton.setText("Включить");
            powerButton.setBackground(COLOR_OFF);
            setStatus("Устройство выключено", COLOR_NEUTRAL);
        }
        updateUIState();
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private void updateUIState() {
        boolean powered = hardwareState.isPoweredOn();
        boolean firmwareLoaded = hardwareState.isFirmwareLoaded();
        boolean fnConnected = hardwareState.isFnConnected();
        boolean coverOpen = hardwareState.isCoverOpen();

        powerButton.setEnabled(firmwareLoaded);

        boolean opReady = powered && firmwareLoaded && !coverOpen && fnConnected;
        openSessionButton.setEnabled(opReady
            && fiscalAccumulator.getState() != FiscalAccumulator.FnState.OPEN_SESSION);
        closeSessionButton.setEnabled(opReady
            && fiscalAccumulator.getState() == FiscalAccumulator.FnState.OPEN_SESSION);
        printTestButton.setEnabled(opReady
            && fiscalAccumulator.getState() == FiscalAccumulator.FnState.OPEN_SESSION);

        // Update hardware status register label
        String hwStatus = String.format("0x%02X", hardwareState.getStatusByte());
        findComponentByName(getContentPane(), "hwStatusLabel").ifPresent(c -> {
            if (c instanceof JLabel lbl) lbl.setText(hwStatus);
        });

        // Update power LED
        findComponentByName(getContentPane(), "powerLed").ifPresent(c -> {
            c.setBackground(powered ? COLOR_ON : COLOR_OFF);
            c.repaint();
        });

        repaint();
    }

    private void styleToggle(JToggleButton btn, boolean activeState, String offText, String onText) {
        btn.setText(btn.isSelected() ? onText : offText);
        if (btn == coverToggle) {
            // Cover open = bad state
            btn.setBackground(btn.isSelected() ? COLOR_WARNING : COLOR_ON);
        } else {
            btn.setBackground(btn.isSelected() ? COLOR_ON : COLOR_WARNING);
        }
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 11f));
    }

    private void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusIndicator.setBackground(color);
        statusIndicator.repaint();
    }

    private java.util.Optional<Component> findComponentByName(Container container, String name) {
        for (Component c : container.getComponents()) {
            if (name.equals(c.getName())) return java.util.Optional.of(c);
            if (c instanceof Container sub) {
                var found = findComponentByName(sub, name);
                if (found.isPresent()) return found;
            }
        }
        return java.util.Optional.empty();
    }
}
