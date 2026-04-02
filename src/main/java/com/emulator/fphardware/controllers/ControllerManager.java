package com.emulator.fphardware.controllers;

import com.emulator.fphardware.fn.FiscalStorageEmulator;
import com.emulator.fphardware.printer.PrinterEmulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Менеджер контроллеров эмулятора
 * Управляет всеми аппаратными контроллерами и прерываниями
 */
public class ControllerManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ControllerManager.class);
    
    // Контроллеры
    private final PrinterEmulator printer;
    private final FiscalStorageEmulator fiscalStorage;
    private final SensorController sensorController;
    private final PowerController powerController;
    
    // Очередь прерываний
    private final Queue<Integer> interruptQueue = new LinkedList<>();
    
    // Состояние контроллеров
    private boolean powerOn = false;
    private boolean caseClosed = true;
    private boolean paperRollPresent = true;
    private boolean paperPresent = true;
    private boolean fnConnected = true;
    
    public ControllerManager() {
        logger.info("Инициализация менеджера контроллеров");
        
        // Инициализация контроллеров
        this.printer = new PrinterEmulator();
        this.fiscalStorage = new FiscalStorageEmulator();
        this.sensorController = new SensorController();
        this.powerController = new PowerController();
        
        logger.info("Менеджер контроллеров инициализирован");
    }
    
    /**
     * Обновление состояния всех контроллеров
     */
    public void update() {
        if (!powerOn) {
            return;
        }
        
        // Обновление принтера
        printer.update();
        
        // Обновление ФН
        fiscalStorage.update();
        
        // Обновление датчиков
        sensorController.update();
        
        // Проверка прерываний
        checkControllerInterrupts();
    }
    
    /**
     * Проверка прерываний от контроллеров
     */
    private void checkControllerInterrupts() {
        // Прерывание от принтера
        if (printer.hasInterrupt()) {
            interruptQueue.offer(InterruptVector.PRINTER);
        }
        
        // Прерывание от ФН
        if (fiscalStorage.hasInterrupt()) {
            interruptQueue.offer(InterruptVector.FISCAL_STORAGE);
        }
        
        // Прерывание от датчиков
        if (sensorController.hasInterrupt()) {
            interruptQueue.offer(InterruptVector.SENSOR);
        }
        
        // Прерывание от питания
        if (powerController.hasInterrupt()) {
            interruptQueue.offer(InterruptVector.POWER);
        }
    }
    
    /**
     * Получение следующего прерывания из очереди
     */
    public int getNextInterrupt() {
        return interruptQueue.poll();
    }
    
    /**
     * Проверка наличия ожидающих прерываний
     */
    public boolean hasPendingInterrupts() {
        return !interruptQueue.isEmpty();
    }
    
    /**
     * Запись в порт контроллера
     */
    public void writePort(int port, int value) {
        if (!powerOn) {
            logger.warn("Попытка записи в порт при выключенном питании");
            return;
        }
        
        switch (port) {
            case IoPorts.PRINTER_CONTROL:
                printer.writeControl(value);
                break;
                
            case IoPorts.PRINTER_DATA:
                printer.writeData(value);
                break;
                
            case IoPorts.FN_CONTROL:
                fiscalStorage.writeControl(value);
                break;
                
            case IoPorts.FN_DATA:
                fiscalStorage.writeData(value);
                break;
                
            case IoPorts.SENSOR_STATUS:
                sensorController.writeControl(value);
                break;
                
            default:
                logger.warn("Запись в неизвестный порт: 0x" + Integer.toHexString(port));
                break;
        }
    }
    
    /**
     * Чтение из порта контроллера
     */
    public int readPort(int port) {
        if (!powerOn) {
            return 0x00;
        }
        
        switch (port) {
            case IoPorts.PRINTER_STATUS:
                return printer.readStatus();
                
            case IoPorts.PRINTER_DATA:
                return printer.readData();
                
            case IoPorts.FN_STATUS:
                return fiscalStorage.readStatus();
                
            case IoPorts.FN_DATA:
                return fiscalStorage.readData();
                
            case IoPorts.SENSOR_STATUS:
                return sensorController.readStatus();
                
            case IoPorts.POWER_STATUS:
                return powerController.readStatus();
                
            default:
                logger.warn("Чтение из неизвестного порта: 0x" + Integer.toHexString(port));
                return 0xFF;
        }
    }
    
    // Методы управления состоянием
    
    public void setPowerOn(boolean powerOn) {
        this.powerOn = powerOn;
        if (!powerOn) {
            // При выключении питания сбрасываем все контроллеры
            printer.reset();
            fiscalStorage.reset();
            sensorController.reset();
            powerController.reset();
            interruptQueue.clear();
            logger.info("Питание выключено, контроллеры сброшены");
        } else {
            logger.info("Питание включено");
        }
    }
    
    public void setCaseClosed(boolean caseClosed) {
        this.caseClosed = caseClosed;
        sensorController.setCaseClosed(caseClosed);
        
        if (!caseClosed) {
            // При открытой крышке генерируем прерывание
            interruptQueue.offer(InterruptVector.CASE_OPEN);
            logger.info("Крышка корпуса открыта");
        } else {
            logger.info("Крышка корпуса закрыта");
        }
    }
    
    public void setPaperRollPresent(boolean paperRollPresent) {
        this.paperRollPresent = paperRollPresent;
        sensorController.setPaperRollPresent(paperRollPresent);
        
        if (!paperRollPresent) {
            interruptQueue.offer(InterruptVector.PAPER_OUT);
            logger.info("Рулон бумаги отсутствует");
        } else {
            logger.info("Рулон бумаги присутствует");
        }
    }
    
    public void setPaperPresent(boolean paperPresent) {
        this.paperPresent = paperPresent;
        sensorController.setPaperPresent(paperPresent);
        
        if (!paperPresent) {
            interruptQueue.offer(InterruptVector.PAPER_END);
            logger.info("Бумага закончилась");
        } else {
            logger.info("Бумага присутствует");
        }
    }
    
    public void setFnConnected(boolean fnConnected) {
        this.fnConnected = fnConnected;
        fiscalStorage.setConnected(fnConnected);
        
        if (!fnConnected) {
            interruptQueue.offer(InterruptVector.FN_DISCONNECT);
            logger.info("ФН отключен");
        } else {
            logger.info("ФН подключен");
        }
    }
    
    // Getters
    public PrinterEmulator getPrinter() {
        return printer;
    }
    
    public FiscalStorageEmulator getFiscalStorage() {
        return fiscalStorage;
    }
    
    public SensorController getSensorController() {
        return sensorController;
    }
    
    public PowerController getPowerController() {
        return powerController;
    }
    
    public boolean isPowerOn() {
        return powerOn;
    }
    
    public boolean isCaseClosed() {
        return caseClosed;
    }
    
    public boolean isPaperRollPresent() {
        return paperRollPresent;
    }
    
    public boolean isPaperPresent() {
        return paperPresent;
    }
    
    public boolean isFnConnected() {
        return fnConnected;
    }
    
    /**
     * Сброс всех контроллеров
     */
    public void reset() {
        printer.reset();
        fiscalStorage.reset();
        sensorController.reset();
        powerController.reset();
        interruptQueue.clear();
        logger.info("Все контроллеры сброшены");
    }
}
