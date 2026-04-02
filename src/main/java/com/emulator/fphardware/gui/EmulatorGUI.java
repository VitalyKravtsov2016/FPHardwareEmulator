package com.emulator.fphardware.gui;

import com.emulator.fphardware.cpu.CortexM3Emulator;
import com.emulator.fphardware.memory.MemoryManager;
import com.emulator.fphardware.controllers.ControllerManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Главный GUI интерфейс эмулятора фискального регистратора
 */
public class EmulatorGUI {
    
    private static final Logger logger = LoggerFactory.getLogger(EmulatorGUI.class);
    
    // Основные компоненты
    private BorderPane rootPane;
    private CortexM3Emulator cpu;
    private MemoryManager memory;
    private ControllerManager controllers;
    
    // Панели управления
    private VBox controlPanel;
    private VBox statusPanel;
    private TextArea receiptArea;
    private TextArea logArea;
    
    // Элементы управления
    private Button loadFirmwareButton;
    private Button powerButton;
    private ToggleButton caseClosedToggle;
    private ToggleButton paperRollToggle;
    private ToggleButton paperToggle;
    private ToggleButton fnConnectedToggle;
    
    // Метки статуса
    private Label powerStatusLabel;
    private Label cpuStatusLabel;
    private Label memoryStatusLabel;
    private Label printerStatusLabel;
    private Label fnStatusLabel;
    
    // Поток эмуляции
    private Thread emulationThread;
    private volatile boolean running = false;
    
    public EmulatorGUI() {
        logger.info("Инициализация GUI эмулятора");
        
        // Инициализация компонентов эмулятора
        initializeComponents();
        
        // Создание интерфейса
        createInterface();
        
        logger.info("GUI эмулятора инициализирован");
    }
    
    /**
     * Инициализация компонентов эмулятора
     */
    private void initializeComponents() {
        memory = new MemoryManager();
        controllers = new ControllerManager();
        cpu = new CortexM3Emulator(memory, controllers);
    }
    
    /**
     * Создание интерфейса
     */
    private void createInterface() {
        rootPane = new BorderPane();
        rootPane.setPadding(new Insets(10));
        
        // Создание верхней панели с элементами управления
        createTopPanel();
        
        // Создание центральной области с чеком
        createCenterPanel();
        
        // Создание правой панели со статусом
        createRightPanel();
        
        // Создание нижней панели с логом
        createBottomPanel();
    }
    
    /**
     * Создание верхней панели управления
     */
    private void createTopPanel() {
        HBox topPanel = new HBox(10);
        topPanel.setPadding(new Insets(0, 0, 10, 0));
        topPanel.setAlignment(Pos.CENTER_LEFT);
        
        // Кнопка загрузки прошивки
        loadFirmwareButton = new Button("Загрузить прошивку");
        loadFirmwareButton.setOnAction(e -> loadFirmware());
        
        // Кнопка питания
        powerButton = new Button("Включить");
        powerButton.setOnAction(e -> togglePower());
        
        // Переключатели состояния
        caseClosedToggle = new ToggleButton("Крышка закрыта");
        caseClosedToggle.setSelected(true);
        caseClosedToggle.setOnAction(e -> updateCaseStatus());
        
        paperRollToggle = new ToggleButton("Рулон бумаги");
        paperRollToggle.setSelected(true);
        paperRollToggle.setOnAction(e -> updatePaperRollStatus());
        
        paperToggle = new ToggleButton("Бумага");
        paperToggle.setSelected(true);
        paperToggle.setOnAction(e -> updatePaperStatus());
        
        fnConnectedToggle = new ToggleButton("ФН подключен");
        fnConnectedToggle.setSelected(true);
        fnConnectedToggle.setOnAction(e -> updateFnStatus());
        
        topPanel.getChildren().addAll(
            loadFirmwareButton,
            new Separator(Orientation.VERTICAL),
            powerButton,
            new Separator(Orientation.VERTICAL),
            caseClosedToggle,
            paperRollToggle,
            paperToggle,
            fnConnectedToggle
        );
        
        rootPane.setTop(topPanel);
    }
    
    /**
     * Создание центральной области с чеком
     */
    private void createCenterPanel() {
        VBox centerPanel = new VBox(10);
        centerPanel.setAlignment(Pos.CENTER);
        
        Label receiptLabel = new Label("Окно чека:");
        receiptLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        receiptArea = new TextArea();
        receiptArea.setPrefHeight(400);
        receiptArea.setPrefWidth(600);
        receiptArea.setEditable(false);
        receiptArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        receiptArea.setText("Чек будет отображаться здесь...\n\n");
        
        centerPanel.getChildren().addAll(receiptLabel, receiptArea);
        rootPane.setCenter(centerPanel);
    }
    
    /**
     * Создание правой панели со статусом
     */
    private void createRightPanel() {
        statusPanel = new VBox(10);
        statusPanel.setPadding(new Insets(0, 0, 0, 10));
        statusPanel.setPrefWidth(250);
        
        Label statusTitle = new Label("Статус системы:");
        statusTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        // Метки статуса
        powerStatusLabel = new Label("Питание: Выключено");
        cpuStatusLabel = new Label("CPU: Остановлен");
        memoryStatusLabel = new Label("Память: OK");
        printerStatusLabel = new Label("Принтер: Готов");
        fnStatusLabel = new Label("ФН: Отключен");
        
        statusPanel.getChildren().addAll(
            statusTitle,
            new Separator(),
            powerStatusLabel,
            cpuStatusLabel,
            memoryStatusLabel,
            printerStatusLabel,
            fnStatusLabel,
            new Separator(),
            createSystemInfo()
        );
        
        rootPane.setRight(statusPanel);
    }
    
    /**
     * Создание панели с информацией о системе
     */
    private VBox createSystemInfo() {
        VBox infoPanel = new VBox(5);
        
        Label infoTitle = new Label("Информация о системе:");
        infoTitle.setStyle("-fx-font-weight: bold;");
        
        Label romInfo = new Label("ROM: " + (memory.getRomSize() / 1024) + "KB");
        Label ramInfo = new Label("RAM: " + (memory.getRamSize() / 1024) + "KB");
        Label cpuInfo = new Label("Частота CPU: 12 МГц");
        
        infoPanel.getChildren().addAll(infoTitle, romInfo, ramInfo, cpuInfo);
        
        return infoPanel;
    }
    
    /**
     * Создание нижней панели с логом
     */
    private void createBottomPanel() {
        VBox bottomPanel = new VBox(5);
        
        Label logTitle = new Label("Системный лог:");
        logTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        
        logArea = new TextArea();
        logArea.setPrefHeight(150);
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 10px;");
        
        bottomPanel.getChildren().addAll(logTitle, logArea);
        rootPane.setBottom(bottomPanel);
    }
    
    /**
     * Загрузка прошивки
     */
    private void loadFirmware() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл прошивки");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Binary files (*.bin)", "*.bin")
        );
        
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            try {
                byte[] firmwareData = Files.readAllBytes(selectedFile.toPath());
                memory.loadFirmware(firmwareData);
                
                logMessage("Прошивка загружена: " + selectedFile.getName() + 
                          " (" + firmwareData.length + " байт)");
                
                // Сброс процессора после загрузки прошивки
                cpu.reset();
                updateStatus();
                
            } catch (IOException e) {
                logMessage("Ошибка загрузки прошивки: " + e.getMessage());
                showError("Ошибка загрузки", "Не удалось загрузить файл прошивки");
            }
        }
    }
    
    /**
     * Переключение питания
     */
    private void togglePower() {
        if (running) {
            stopEmulation();
        } else {
            startEmulation();
        }
    }
    
    /**
     * Запуск эмуляции
     */
    private void startEmulation() {
        if (running) {
            return;
        }
        
        running = true;
        powerButton.setText("Выключить");
        
        // Включаем питание в контроллерах
        controllers.setPowerOn(true);
        
        // Запускаем процессор
        cpu.start();
        
        // Запускаем поток эмуляции
        emulationThread = new Thread(this::emulationLoop);
        emulationThread.setDaemon(true);
        emulationThread.start();
        
        logMessage("Эмуляция запущена");
        updateStatus();
    }
    
    /**
     * Остановка эмуляции
     */
    private void stopEmulation() {
        if (!running) {
            return;
        }
        
        running = false;
        powerButton.setText("Включить");
        
        // Останавливаем процессор
        cpu.stop();
        
        // Выключаем питание в контроллерах
        controllers.setPowerOn(false);
        
        // Ждем завершения потока эмуляции
        if (emulationThread != null) {
            try {
                emulationThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        logMessage("Эмуляция остановлена");
        updateStatus();
    }
    
    /**
     * Основной цикл эмуляции
     */
    private void emulationLoop() {
        while (running) {
            try {
                // Шаг эмуляции CPU
                cpu.step();
                
                // Обновление контроллеров
                controllers.update();
                
                // Обновление GUI
                Platform.runLater(this::updateGUI);
                
                // Задержка для реалистичности
                Thread.sleep(1);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Ошибка в цикле эмуляции: " + e.getMessage());
                Platform.runLater(() -> logMessage("Ошибка эмуляции: " + e.getMessage()));
                break;
            }
        }
    }
    
    /**
     * Обновление GUI
     */
    private void updateGUI() {
        // Обновление окна чека
        updateReceiptArea();
        
        // Обновление статуса
        updateStatus();
    }
    
    /**
     * Обновление окна чека
     */
    private void updateReceiptArea() {
        if (controllers.getPrinter().getBufferSize() > 0) {
            StringBuilder receiptText = new StringBuilder();
            
            for (String line : controllers.getPrinter().getPrintedText()) {
                receiptText.append(line).append("\n");
            }
            
            if (receiptText.length() > 0) {
                receiptArea.setText(receiptText.toString());
            }
        }
    }
    
    /**
     * Обновление статуса
     */
    private void updateStatus() {
        // Статус питания
        powerStatusLabel.setText("Питание: " + 
            (controllers.isPowerOn() ? "Включено" : "Выключено"));
        
        // Статус CPU
        cpuStatusLabel.setText("CPU: " + 
            (cpu.isRunning() ? "Работает" : cpu.isFaulted() ? "Fault" : cpu.isSleeping() ? "Sleep" : "Остановлен"));
        
        // Статус принтера
        String printerStatus = "Принтер: ";
        if (!controllers.isPowerOn()) {
            printerStatus += "Нет питания";
        } else if (controllers.getPrinter().isError()) {
            printerStatus += "Ошибка";
        } else if (controllers.getPrinter().isPrinting()) {
            printerStatus += "Печать";
        } else {
            printerStatus += "Готов";
        }
        printerStatusLabel.setText(printerStatus);
        
        // Статус ФН
        String fnStatus = "ФН: ";
        if (!controllers.isFnConnected()) {
            fnStatus += "Отключен";
        } else if (!controllers.isPowerOn()) {
            fnStatus += "Нет питания";
        } else if (controllers.getFiscalStorage().isError()) {
            fnStatus += "Ошибка";
        } else if (controllers.getFiscalStorage().isBusy()) {
            fnStatus += "Занят";
        } else {
            fnStatus += "Готов";
        }
        fnStatusLabel.setText(fnStatus);
    }
    
    /**
     * Обновление статуса крышки
     */
    private void updateCaseStatus() {
        controllers.setCaseClosed(caseClosedToggle.isSelected());
        logMessage("Крышка корпуса: " + (caseClosedToggle.isSelected() ? "закрыта" : "открыта"));
    }
    
    /**
     * Обновление статуса рулона бумаги
     */
    private void updatePaperRollStatus() {
        controllers.setPaperRollPresent(paperRollToggle.isSelected());
        logMessage("Рулон бумаги: " + (paperRollToggle.isSelected() ? "присутствует" : "отсутствует"));
    }
    
    /**
     * Обновление статуса бумаги
     */
    private void updatePaperStatus() {
        controllers.setPaperPresent(paperToggle.isSelected());
        logMessage("Бумага: " + (paperToggle.isSelected() ? "присутствует" : "отсутствует"));
    }
    
    /**
     * Обновление статуса ФН
     */
    private void updateFnStatus() {
        controllers.setFnConnected(fnConnectedToggle.isSelected());
        logMessage("ФН: " + (fnConnectedToggle.isSelected() ? "подключен" : "отключен"));
    }
    
    /**
     * Добавление сообщения в лог
     */
    private void logMessage(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().toString();
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            
            // Автопрокрутка вниз
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }
    
    /**
     * Показ диалога ошибки
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Завершение работы приложения
     */
    public void shutdown() {
        stopEmulation();
        logger.info("Эмулятор завершает работу");
    }
    
    /**
     * Получение корневой панели
     */
    public BorderPane getRootPane() {
        return rootPane;
    }
}
