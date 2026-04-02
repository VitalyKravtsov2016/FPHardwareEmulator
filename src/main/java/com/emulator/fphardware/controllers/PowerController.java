package com.emulator.fphardware.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Контроллер питания фискального регистратора
 */
public class PowerController {
    
    private static final Logger logger = LoggerFactory.getLogger(PowerController.class);
    
    // Состояние питания
    private boolean mainPowerOn = false;
    private boolean batteryPowerOn = false;
    private boolean lowBattery = false;
    private boolean powerFailure = false;
    
    // Флаг прерывания
    private boolean interruptRequested = false;
    
    public PowerController() {
        logger.debug("Контроллер питания инициализирован");
    }
    
    /**
     * Обновление состояния питания
     */
    public void update() {
        // Проверка состояния батареи
        if (batteryPowerOn && !mainPowerOn) {
            // Имитация разряда батареи
            // В реальной эмуляции здесь была бы логика разряда
        }
    }
    
    /**
     * Чтение статуса питания
     */
    public int readStatus() {
        int status = 0;
        
        // Бит 0 - основное питание включено
        if (mainPowerOn) {
            status |= 0x01;
        }
        
        // Бит 1 - питание от батареи включено
        if (batteryPowerOn) {
            status |= 0x02;
        }
        
        // Бит 2 - низкий заряд батареи
        if (lowBattery) {
            status |= 0x04;
        }
        
        // Бит 3 - сбой питания
        if (powerFailure) {
            status |= 0x08;
        }
        
        return status;
    }
    
    /**
     * Запись управляющего регистра
     */
    public void writeControl(int value) {
        // Управление питанием (в реальном устройстве здесь была бы логика)
        logger.debug("Запись в регистр управления питанием: 0x" + 
                    Integer.toHexString(value));
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
        mainPowerOn = false;
        batteryPowerOn = false;
        lowBattery = false;
        powerFailure = false;
        interruptRequested = false;
        
        logger.debug("Контроллер питания сброшен");
    }
    
    // Setters
    public void setMainPowerOn(boolean mainPowerOn) {
        this.mainPowerOn = mainPowerOn;
        if (!mainPowerOn) {
            // При отключении основного питания переходим на батарею
            batteryPowerOn = true;
        }
    }
    
    public void setBatteryPowerOn(boolean batteryPowerOn) {
        this.batteryPowerOn = batteryPowerOn;
    }
    
    public void setLowBattery(boolean lowBattery) {
        this.lowBattery = lowBattery;
        if (lowBattery) {
            interruptRequested = true;
        }
    }
    
    public void setPowerFailure(boolean powerFailure) {
        this.powerFailure = powerFailure;
        if (powerFailure) {
            interruptRequested = true;
        }
    }
    
    // Getters
    public boolean isMainPowerOn() {
        return mainPowerOn;
    }
    
    public boolean isBatteryPowerOn() {
        return batteryPowerOn;
    }
    
    public boolean isLowBattery() {
        return lowBattery;
    }
    
    public boolean isPowerFailure() {
        return powerFailure;
    }
}
