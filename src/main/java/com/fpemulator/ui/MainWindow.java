package com.fpemulator.ui;

import com.fpemulator.comport.ProtocolHandler;
import com.fpemulator.comport.VirtualComPort;
import com.fpemulator.cpu.LPC1778;
import com.fpemulator.fn.FiscalAccumulator;
import com.fpemulator.fn.FiscalRecord;
import com.fpemulator.fn.ReceiptItem;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.logging.Logger;

/**
 * Main application window for the FP Hardware Emulator.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────┐
 *   │  Menu bar                                   │
 *   ├──────────────────┬──────────────────────────┤
 *   │  ControlPanel    │  ReceiptPanel            │
 *   │  (left)          │  (right, expandable)     │
 *   └──────────────────┴──────────────────────────┘
 */
public class MainWindow extends JFrame {

    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());

    private static final int    DEFAULT_TCP_PORT = 7890;
    private static final String KKT_REG_NUMBER   = "0000000001045673";
    private static final String INN              = "770100000021";
    private static final String SHOP_NAME        = "ООО «Эмулятор»";
    private static final String ADDRESS          = "г. Москва, ул. Тестовая, 1";

    // Core components
    private final LPC1778           mcu;
    private final FiscalAccumulator fn;
    private final VirtualComPort    comPort;
    private final ProtocolHandler   protocolHandler;

    // UI components
    private final ControlPanel controlPanel;
    private final ReceiptPanel receiptPanel;

    // Background polling timer (UART TX flush + status refresh)
    private final Timer uiTimer;

    public MainWindow() {
        super("FP Hardware Emulator — ФРК эмулятор (LPC1778 / Shtrikh-M 01F)");

        // ── Instantiate core emulator components ──────────────────────────
        mcu     = new LPC1778();
        fn      = new FiscalAccumulator(KKT_REG_NUMBER, INN, SHOP_NAME, ADDRESS);
        comPort = new VirtualComPort(DEFAULT_TCP_PORT);

        // Wire FN receipt-closed event → UI
        fn.setReceiptClosedListener(this::onReceiptClosed);

        // Create protocol handler (bridges UART0 ↔ COM port ↔ FN demo commands)
        protocolHandler = new ProtocolHandler(comPort, mcu.getPeripherals().getUART(0), fn);

        // ── Build UI ──────────────────────────────────────────────────────
        controlPanel = new ControlPanel();
        receiptPanel = new ReceiptPanel();

        // Start virtual COM port (after controlPanel is initialized so callbacks work)
        try {
            comPort.open();
            comPort.setConnectCallback(()    -> controlPanel.updateComPortState(true, DEFAULT_TCP_PORT));
            comPort.setDisconnectCallback(() -> controlPanel.updateComPortState(false, DEFAULT_TCP_PORT));
        } catch (IOException e) {
            LOG.warning("Could not open virtual COM port: " + e.getMessage());
        }
        receiptPanel.setShopInfo(SHOP_NAME, INN, ADDRESS);

        wireControlPanelActions();
        buildLayout();
        buildMenuBar();

        // ── UI refresh timer (10 ms) ──────────────────────────────────────
        uiTimer = new Timer(100, e -> refreshUI());
        uiTimer.start();

        // ── Window settings ───────────────────────────────────────────────
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { onWindowClosing(); }
        });

        pack();
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setVisible(true);

        controlPanel.log("Эмулятор запущен. Виртуальный COM порт: TCP:" + DEFAULT_TCP_PORT);
        controlPanel.log("Для управления через сеть подключитесь к localhost:" + DEFAULT_TCP_PORT);
        controlPanel.updateComPortState(false, DEFAULT_TCP_PORT);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void buildLayout() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, receiptPanel);
        split.setResizeWeight(0.38);
        split.setDividerLocation(400);
        getContentPane().add(split, BorderLayout.CENTER);
    }

    private void buildMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu menuFile = new JMenu("Файл");
        JMenuItem miLoad = new JMenuItem("Загрузить прошивку…");
        miLoad.addActionListener(e -> loadFirmwareDialog());
        JMenuItem miExit = new JMenuItem("Выход");
        miExit.addActionListener(e -> onWindowClosing());
        menuFile.add(miLoad);
        menuFile.addSeparator();
        menuFile.add(miExit);

        JMenu menuEmulator = new JMenu("Эмулятор");
        JMenuItem miStart = new JMenuItem("Запустить");
        miStart.addActionListener(e -> startCPU());
        JMenuItem miStop = new JMenuItem("Остановить");
        miStop.addActionListener(e -> stopCPU());
        JMenuItem miReset = new JMenuItem("Сброс");
        miReset.addActionListener(e -> resetMCU());
        menuEmulator.add(miStart);
        menuEmulator.add(miStop);
        menuEmulator.addSeparator();
        menuEmulator.add(miReset);

        JMenu menuHelp = new JMenu("Справка");
        JMenuItem miAbout = new JMenuItem("О программе");
        miAbout.addActionListener(e -> showAbout());
        menuHelp.add(miAbout);

        mb.add(menuFile);
        mb.add(menuEmulator);
        mb.add(menuHelp);
        setJMenuBar(mb);
    }

    // ── Control panel wiring ─────────────────────────────────────────────────

    private void wireControlPanelActions() {
        controlPanel.onLoadFirmware(e -> loadFirmwareDialog());
        controlPanel.onStart(e -> startCPU());
        controlPanel.onStop(e -> stopCPU());
        controlPanel.onStep(e -> stepCPU());
        controlPanel.onReset(e -> resetMCU());

        // FN demo controls
        controlPanel.onOpenShift(e -> {
            try {
                fn.openShift();
                controlPanel.log("Смена #" + fn.getShiftNumber() + " открыта");
            } catch (Exception ex) {
                controlPanel.log("Ошибка: " + ex.getMessage());
            }
        });

        controlPanel.onCloseShift(e -> {
            try {
                fn.closeShift();
                controlPanel.log("Смена закрыта");
                receiptPanel.clearCurrentReceipt();
            } catch (Exception ex) {
                controlPanel.log("Ошибка: " + ex.getMessage());
            }
        });

        controlPanel.onOpenReceipt(e -> {
            try {
                fn.openReceipt(FiscalRecord.Type.SALE);
                controlPanel.log("Чек #" + fn.getReceiptNumber() + " открыт");
                receiptPanel.updateCurrentReceipt(buildOpenReceiptText());
            } catch (Exception ex) {
                controlPanel.log("Ошибка: " + ex.getMessage());
            }
        });

        controlPanel.onAddItem(e -> showAddItemDialog());

        controlPanel.onCloseReceipt(e -> showCloseReceiptDialog());

        controlPanel.onCancelReceipt(e -> {
            try {
                fn.cancelReceipt();
                controlPanel.log("Чек отменён");
                receiptPanel.clearCurrentReceipt();
            } catch (Exception ex) {
                controlPanel.log("Ошибка: " + ex.getMessage());
            }
        });
    }

    // ── CPU lifecycle ─────────────────────────────────────────────────────────

    private void loadFirmwareDialog() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Выберите файл прошивки (.bin)");
        fc.setFileFilter(new FileNameExtensionFilter("Binary firmware (*.bin)", "bin"));
        fc.setAcceptAllFileFilterUsed(true);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try (InputStream is = new FileInputStream(f)) {
                mcu.loadFirmware(is);
                controlPanel.log("Прошивка загружена: " + f.getName()
                        + " (" + f.length() + " байт)");
                controlPanel.setStatus("Прошивка загружена: " + f.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Ошибка загрузки прошивки:\n" + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void startCPU() {
        if (!mcu.isFirmwareLoaded()) {
            JOptionPane.showMessageDialog(this,
                    "Прошивка не загружена.\nЗагрузите .bin файл перед запуском.",
                    "Нет прошивки", JOptionPane.WARNING_MESSAGE);
            return;
        }
        mcu.reset();
        mcu.start();
        controlPanel.setRunning(true);
        controlPanel.log("CPU запущен");
    }

    private void stopCPU() {
        mcu.stop();
        controlPanel.setRunning(false);
        controlPanel.log("CPU остановлен. Выполнено команд: "
                + mcu.getCpu().getInstructionCount());
    }

    private void stepCPU() {
        if (!mcu.isFirmwareLoaded()) {
            controlPanel.log("Прошивка не загружена");
            return;
        }
        boolean ok = mcu.step();
        controlPanel.updateInstrCount(mcu.getCpu().getInstructionCount());
        if (!ok) controlPanel.log("CPU остановлен (halt/breakpoint)");
    }

    private void resetMCU() {
        mcu.stop();
        mcu.reset();
        controlPanel.setRunning(false);
        controlPanel.updateInstrCount(0);
        controlPanel.log("MCU сброшен");
    }

    // ── FN UI helpers ─────────────────────────────────────────────────────────

    private void showAddItemDialog() {
        JTextField fldName  = new JTextField("Товар", 20);
        JTextField fldQty   = new JTextField("1.000", 8);
        JTextField fldPrice = new JTextField("100.00", 8);
        JComboBox<String> cmbVat = new JComboBox<>(new String[]{"18%", "10%", "0%", "Без НДС"});

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel("Наименование:"));  panel.add(fldName);
        panel.add(new JLabel("Количество:"));    panel.add(fldQty);
        panel.add(new JLabel("Цена (руб.):"));   panel.add(fldPrice);
        panel.add(new JLabel("НДС:"));            panel.add(cmbVat);

        int res = JOptionPane.showConfirmDialog(this, panel, "Добавить позицию",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        try {
            String name  = fldName.getText().trim();
            int    qty   = Math.round(Float.parseFloat(fldQty.getText().replace(',', '.')) * 1000);
            long   price = Math.round(Double.parseDouble(fldPrice.getText().replace(',', '.')) * 100);
            int    vat   = cmbVat.getSelectedIndex() + 1;

            fn.addItem(name, qty, price, vat);
            controlPanel.log(String.format("Добавлено: %s × %.3f × %.2f руб.",
                    name, qty / 1000.0, price / 100.0));
            receiptPanel.updateCurrentReceipt(buildOpenReceiptText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Неверный формат числа",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            controlPanel.log("Ошибка: " + ex.getMessage());
        }
    }

    private void showCloseReceiptDialog() {
        FiscalRecord cur = fn.getCurrentReceipt();
        if (cur == null) {
            controlPanel.log("Нет открытого чека");
            return;
        }
        double totalRub = cur.getTotalAmount() / 100.0;
        JTextField fldCash = new JTextField(String.format("%.2f", totalRub), 10);
        JTextField fldCard = new JTextField("0.00", 10);

        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
        panel.add(new JLabel(String.format("Итого: %.2f руб.", totalRub)));
        panel.add(new JLabel(""));
        panel.add(new JLabel("Наличными (руб.):"));  panel.add(fldCash);
        panel.add(new JLabel("Картой (руб.):"));      panel.add(fldCard);

        int res = JOptionPane.showConfirmDialog(this, panel, "Закрыть чек",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        try {
            long cash = Math.round(Double.parseDouble(fldCash.getText().replace(',', '.')) * 100);
            long card = Math.round(Double.parseDouble(fldCard.getText().replace(',', '.')) * 100);
            fn.closeReceipt(cash, card);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Неверный формат числа",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            controlPanel.log("Ошибка: " + ex.getMessage());
        }
    }

    /** Called from FiscalAccumulator when a receipt is closed. */
    private void onReceiptClosed(FiscalRecord record) {
        receiptPanel.addClosedReceipt(record);
        controlPanel.log(String.format("Чек #%d закрыт. Сумма: %.2f руб.",
                record.getNumber(), record.getTotalAmount() / 100.0));
    }

    /** Build a running receipt display for the currently open receipt. */
    private String buildOpenReceiptText() {
        FiscalRecord cur = fn.getCurrentReceipt();
        if (cur == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(44)).append("\n");
        sb.append("           ОТКРЫТЫЙ ЧЕК\n");
        sb.append("-".repeat(44)).append("\n");
        sb.append("Смена #").append(fn.getShiftNumber())
          .append("  Чек #").append(fn.getReceiptNumber()).append("\n");
        sb.append(cur.getType().getLabel()).append("\n");
        sb.append("-".repeat(44)).append("\n");
        for (ReceiptItem item : cur.getItems()) {
            sb.append(item).append("\n");
        }
        sb.append("-".repeat(44)).append("\n");
        sb.append(String.format("ИТОГО: %.2f руб.\n", cur.getTotalAmount() / 100.0));
        return sb.toString();
    }

    // ── UI refresh ────────────────────────────────────────────────────────────

    private void refreshUI() {
        protocolHandler.tick();
        controlPanel.updateFnState(fn.getState().name()
                + " | Смена: " + fn.getShiftNumber()
                + " | Чеков: " + fn.getReceiptNumber());
        if (mcu.isRunning()) {
            controlPanel.updateInstrCount(mcu.getCpu().getInstructionCount());
        }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "FP Hardware Emulator\n" +
                "Эмулятор фискального регистратора\n" +
                "Shtrikh-M 01F / LPC1778\n\n" +
                "Версия: 1.0.0\n\n" +
                "Процессор: NXP LPC1778 (ARM Cortex-M3)\n" +
                "Фискальный накопитель: ФН-1.1\n\n" +
                "Виртуальный COM порт: TCP:" + DEFAULT_TCP_PORT,
                "О программе", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onWindowClosing() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Выйти из эмулятора?", "Выход",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            uiTimer.stop();
            mcu.stop();
            comPort.close();
            dispose();
            System.exit(0);
        }
    }
}
