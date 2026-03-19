package com.fpemulator.ui;

import com.fpemulator.cpu.LPC1778;
import com.fpemulator.fn.FiscalAccumulator;
import com.fpemulator.fn.FiscalRecord;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

/**
 * Control panel providing the main emulator controls:
 *   – Load firmware
 *   – Start / Stop / Step CPU
 *   – FN demo controls (open shift, add item, etc.)
 *   – Status display
 */
public class ControlPanel extends JPanel {

    private final JButton btnLoad    = new JButton("📂 Загрузить прошивку…");
    private final JButton btnStart   = new JButton("▶ Запустить");
    private final JButton btnStop    = new JButton("⏹ Остановить");
    private final JButton btnStep    = new JButton("⤵ Шаг");
    private final JButton btnReset   = new JButton("↺ Сброс");

    // FN demo controls
    private final JButton btnOpenShift    = new JButton("Открыть смену");
    private final JButton btnCloseShift   = new JButton("Закрыть смену");
    private final JButton btnOpenReceipt  = new JButton("Открыть чек");
    private final JButton btnCloseReceipt = new JButton("Закрыть чек");
    private final JButton btnCancelReceipt = new JButton("Отмена чека");
    private final JButton btnAddItem      = new JButton("Добавить позицию");

    private final JLabel  lblStatus    = new JLabel("Статус: Готов");
    private final JLabel  lblFnState   = new JLabel("ФН: —");
    private final JLabel  lblCpuState  = new JLabel("CPU: Остановлен");
    private final JLabel  lblInstrCount = new JLabel("Команд: 0");
    private final JLabel  lblComPort   = new JLabel("COM: Не подключён");

    // Log area
    private final JTextArea logArea = new JTextArea(8, 60);

    public ControlPanel() {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Управление",
                TitledBorder.LEFT, TitledBorder.TOP));

        // ── CPU controls ───────────────────────────────────────────────────
        JPanel cpuPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        cpuPanel.setBorder(BorderFactory.createTitledBorder("Процессор LPC1778"));
        cpuPanel.add(btnLoad);
        cpuPanel.add(btnReset);
        cpuPanel.add(btnStart);
        cpuPanel.add(btnStop);
        cpuPanel.add(btnStep);

        btnStop.setEnabled(false);
        btnStep.setEnabled(true);

        // ── FN controls ────────────────────────────────────────────────────
        JPanel fnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        fnPanel.setBorder(BorderFactory.createTitledBorder("Фискальный накопитель (демо)"));
        fnPanel.add(btnOpenShift);
        fnPanel.add(btnCloseShift);
        fnPanel.add(new JSeparator(SwingConstants.VERTICAL));
        fnPanel.add(btnOpenReceipt);
        fnPanel.add(btnAddItem);
        fnPanel.add(btnCloseReceipt);
        fnPanel.add(btnCancelReceipt);

        // ── Status panel ───────────────────────────────────────────────────
        JPanel statusPanel = new JPanel(new GridLayout(2, 2, 4, 2));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Состояние"));
        statusPanel.add(lblCpuState);
        statusPanel.add(lblFnState);
        statusPanel.add(lblInstrCount);
        statusPanel.add(lblComPort);

        // ── Log area ────────────────────────────────────────────────────────
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Журнал"));

        // ── Layout ──────────────────────────────────────────────────────────
        JPanel top = new JPanel(new GridLayout(3, 1, 2, 2));
        top.add(cpuPanel);
        top.add(fnPanel);
        top.add(statusPanel);

        add(top,       BorderLayout.NORTH);
        add(logScroll, BorderLayout.CENTER);
        add(lblStatus, BorderLayout.SOUTH);
    }

    // ── Button action setters ─────────────────────────────────────────────────

    public void onLoadFirmware(ActionListener l)   { btnLoad.addActionListener(l); }
    public void onStart(ActionListener l)          { btnStart.addActionListener(l); }
    public void onStop(ActionListener l)           { btnStop.addActionListener(l); }
    public void onStep(ActionListener l)           { btnStep.addActionListener(l); }
    public void onReset(ActionListener l)          { btnReset.addActionListener(l); }
    public void onOpenShift(ActionListener l)      { btnOpenShift.addActionListener(l); }
    public void onCloseShift(ActionListener l)     { btnCloseShift.addActionListener(l); }
    public void onOpenReceipt(ActionListener l)    { btnOpenReceipt.addActionListener(l); }
    public void onCloseReceipt(ActionListener l)   { btnCloseReceipt.addActionListener(l); }
    public void onCancelReceipt(ActionListener l)  { btnCancelReceipt.addActionListener(l); }
    public void onAddItem(ActionListener l)        { btnAddItem.addActionListener(l); }

    // ── Status updates ────────────────────────────────────────────────────────

    public void setRunning(boolean running) {
        SwingUtilities.invokeLater(() -> {
            btnStart.setEnabled(!running);
            btnStop.setEnabled(running);
            btnLoad.setEnabled(!running);
            btnReset.setEnabled(!running);
            lblCpuState.setText("CPU: " + (running ? "Выполняется" : "Остановлен"));
        });
    }

    public void updateInstrCount(long count) {
        SwingUtilities.invokeLater(() ->
                lblInstrCount.setText(String.format("Команд: %,d", count)));
    }

    public void updateFnState(String state) {
        SwingUtilities.invokeLater(() -> lblFnState.setText("ФН: " + state));
    }

    public void updateComPortState(boolean connected, int port) {
        SwingUtilities.invokeLater(() -> lblComPort.setText(
                connected ? "COM: TCP:" + port + " подключён"
                          : "COM: TCP:" + port + " ожидание…"));
    }

    public void setStatus(String text) {
        SwingUtilities.invokeLater(() -> lblStatus.setText("Статус: " + text));
    }

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
