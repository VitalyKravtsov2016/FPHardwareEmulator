package com.emulator.fphardware.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Контроллер датчиков фискального регистратора
 */
public class SensorController {
    
    private static final Logger logger = LoggerFactory.getLogger(SensorController.class);
    
    // Состояние датчиков
    private boolean caseClosed = true;
    private boolean paperRollPresent = true;
    private boolean paperPresent = true;
    private boolean thermalHeadOk = true;
    private boolean batteryOk = true;
    
    // Флаги прерываний
    private boolean interruptRequested = false;
    private int interruptMask = 0xFF;
    
    public SensorController() {
        logger.debug("Контроллер датчиков инициализирован");
    }
    
    /**
     * Обновление состояния датчиков
     */
    public void update() {
        // Проверка изменений состояния датчиков
        checkStateChanges();
    }
    
    /**
     * Проверка изменений состояния для генерации прерываний
     */
    private void checkStateChanges() {
        // Здесь может быть логика для определения изменений
        // и генерации соответствующих прерываний
    }
    
    /**
     * Чтение статуса датчиков
     */
    public int readStatus() {
        int status = 0;
        
        // Бит 0 - крышка корпуса закрыта
        if (caseClosed) {
            status |= 0x01;
        }
        
        // Бит 1 - рулон бумаги присутствует
        if (paperRollPresent) {
            status |= 0x02;
        }
        
        // Бит 2 - бумага присутствует
        if (paperPresent) {
            status |= 0x04;
        }
        
        // Бит 3 - термоголовка в порядке
        if (thermalHeadOk) {
            status |= 0x08;
        }
        
        // Бит 4 - батарея в порядке
        if (batteryOk) {
            status |= 0x10;
        }
        
        return status;
    }
    
    /**
     * Запись управляющего регистра
     */
    public void writeControl(int value) {
        interruptMask = value & 0xFF;
        logger.debug("Установлена маска прерываний датчиков: 0x" + 
                    Integer.toHexString(interruptMask));
    }
    
    /**
     * Проверка наличия прерывания
     */
    public boolean hasInterrupt() {
        return interruptRequested;
    }
    
    /**
     * Сброс контроллера
     */
    public void reset() {
        caseClosed = true;
        paperRollPresent = true;
        paperPresent = true;
        thermalHeadOk = true;
        batteryOk = true;
        interruptRequested = false;
        interruptMask = 0xFF;
        
        logger.debug("Контроллер датчиков сброшен");
    }
    
    // Setters
    public void setCaseClosed(boolean caseClosed) {
        this.caseClosed = caseClosed;
    }
    
    public void setPaperRollPresent(boolean paperRollPresent) {
        this.paperRollPresent = paperRollPresent;
    }
    
    public void setPaperPresent(boolean paperPresent) {
        this.paperPresent = paperPresent;
    }
    
    public void setThermalHeadOk(boolean thermalHeadOk) {
        this.thermalHeadOk = thermalHeadOk;
    }
    
    public void setBatteryOk(boolean batteryOk) {
        this.batteryOk = batteryOk;
    }
    
    // Getters
    public boolean isCaseClosed() {
        return caseClosed;
    }
    
    public boolean isPaperRollPresent() {
        return paperRollPresent;
    }
    
    public boolean isPaperPresent() {
        return paperPresent;
    }
    
    public boolean isThermalHeadOk() {
        return thermalHeadOk;
    }
    
    public boolean isBatteryOk() {
        return batteryOk;
    }
}
