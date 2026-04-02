package com.emulator.fphardware.i2c;

import com.emulator.fphardware.fn.FiscalStorageEmulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * I2C контроллер LPC1778FBD208
 * Управляет обменом по I2C с фискальным накопителем
 */
public class I2CController {
    
    private static final Logger logger = LoggerFactory.getLogger(I2CController.class);
    
    // Регистры I2C контроллера LPC1778
    private int i2cConset = 0;    // I2C Control Set Register
    private int i2cConclr = 0;    // I2C Control Clear Register
    private int i2cStat = 0;     // I2C Status Register
    private int i2cDat = 0;      // I2C Data Register
    private int i2cAdr = 0;       // I2C Slave Address Register
    private int i2cSclh = 0;     // I2C SCL High Duty Cycle Register
    private int i2cScll = 0;      // I2C SCL Low Duty Cycle Register
    
    // Состояние I2C
    private boolean enabled = false;
    private boolean masterMode = true;
    private int currentState = 0; // Текущее состояние I2C
    
    // Подключенные устройства
    private FiscalStorageEmulator fnDevice;
    
    // Буферы для передачи
    private byte[] transmitBuffer = new byte[256];
    private byte[] receiveBuffer = new byte[256];
    private int transmitIndex = 0;
    private int transmitLength = 0;
    private int receiveIndex = 0;
    private int receiveLength = 0;
    
    // Флаги состояния
    private boolean startSent = false;
    private boolean stopSent = false;
    private boolean ackReceived = false;
    private boolean arbitrationLost = false;
    
    public I2CController() {
        logger.info("Инициализация I2C контроллера LPC1778");
        reset();
    }
    
    /**
     * Сброс I2C контроллера
     */
    public void reset() {
        logger.info("Сброс I2C контроллера");
        
        i2cConset = 0;
        i2cConclr = 0;
        i2cStat = 0xF8; // Idle state
        i2cDat = 0;
        i2cAdr = 0;
        i2cSclh = 0;
        i2cScll = 0;
        
        enabled = false;
        masterMode = true;
        currentState = 0xF8;
        
        transmitIndex = 0;
        transmitLength = 0;
        receiveIndex = 0;
        receiveLength = 0;
        
        startSent = false;
        stopSent = false;
        ackReceived = false;
        arbitrationLost = false;
        
        // Очистка буферов
        for (int i = 0; i < 256; i++) {
            transmitBuffer[i] = 0;
            receiveBuffer[i] = 0;
        }
    }
    
    /**
     * Подключение ФН устройства
     */
    public void connectFN(FiscalStorageEmulator fn) {
        this.fnDevice = fn;
        logger.info("ФН подключен к I2C контроллеру");
    }
    
    /**
     * Включение I2C контроллера
     */
    public void enable() {
        enabled = true;
        i2cConset |= 0x40; // I2EN bit
        logger.info("I2C контроллер включен");
    }
    
    /**
     * Выключение I2C контроллера
     */
    public void disable() {
        enabled = false;
        i2cConclr = 0x40; // Clear I2EN bit
        logger.info("I2C контроллер выключен");
    }
    
    /**
     * Установка режима Master
     */
    public void setMasterMode() {
        masterMode = true;
        i2cConset |= 0x20; // AA bit
        logger.debug("I2C режим: Master");
    }
    
    /**
     * Запуск START условия
     */
    public void startCondition() {
        if (!enabled || !masterMode) {
            return;
        }
        
        logger.debug("I2C START условие");
        startSent = true;
        stopSent = false;
        
        // Установка флага STA
        i2cConset |= 0x20;
        
        // Обновление состояния
        if (fnDevice != null && fnDevice.isConnected()) {
            fnDevice.i2cStartCondition();
            currentState = 0x08; // START sent
        } else {
            currentState = 0x38; // Arbitration lost
        }
        
        updateStatusRegister();
    }
    
    /**
     * Запуск STOP условия
     */
    public void stopCondition() {
        if (!enabled || !masterMode) {
            return;
        }
        
        logger.debug("I2C STOP условие");
        stopSent = true;
        startSent = false;
        
        // Установка флага STO
        i2cConset |= 0x10;
        
        // Обработка STOP в ФН
        if (fnDevice != null && fnDevice.isConnected()) {
            fnDevice.i2cStopCondition();
        }
        
        currentState = 0xF8; // Idle
        updateStatusRegister();
        
        // Сброс флагов
        transmitLength = 0;
        transmitIndex = 0;
        receiveLength = 0;
        receiveIndex = 0;
    }
    
    /**
     * Запись байта в I2C
     */
    public boolean writeByte(int data) {
        if (!enabled || !masterMode) {
            return false;
        }
        
        data &= 0xFF;
        i2cDat = data;
        
        logger.debug("I2C запись байта: 0x{}", Integer.toHexString(data));
        
        // Отправка данных в ФН
        if (fnDevice != null && fnDevice.isConnected()) {
            boolean ack = fnDevice.i2cWriteByte(data);
            ackReceived = ack;
            
            if (ack) {
                currentState = 0x18; // SLA+W sent, ACK received
                if (startSent) {
                    currentState = 0x28; // Data sent, ACK received
                }
            } else {
                currentState = 0x20; // SLA+W sent, NACK received
                if (startSent) {
                    currentState = 0x30; // Data sent, NACK received
                }
            }
        } else {
            // Устройство не подключено
            currentState = 0x20; // NACK
            ackReceived = false;
        }
        
        updateStatusRegister();
        return ackReceived;
    }
    
    /**
     * Чтение байта из I2C
     */
    public int readByte(boolean sendAck) {
        if (!enabled || !masterMode) {
            return 0xFF;
        }
        
        int data = 0xFF;
        
        // Чтение данных из ФН
        if (fnDevice != null && fnDevice.isConnected()) {
            data = fnDevice.i2cReadByte();
            
            if (sendAck) {
                currentState = 0x50; // Data received, ACK sent
                i2cConset |= 0x04; // AA bit
            } else {
                currentState = 0x58; // Data received, NACK sent
                i2cConclr = 0x04; // Clear AA bit
            }
        } else {
            currentState = 0x58; // NACK
            data = 0xFF;
        }
        
        i2cDat = data;
        updateStatusRegister();
        
        logger.debug("I2C чтение байта: 0x{}, ACK: {}", 
                    Integer.toHexString(data), sendAck);
        
        return data;
    }
    
    /**
     * Запись данных в ФН
     */
    public boolean writeFNData(byte[] data) {
        if (!enabled || fnDevice == null || !fnDevice.isConnected()) {
            return false;
        }
        
        // Очистка буфера передачи
        transmitLength = 0;
        transmitIndex = 0;
        
        // Копирование данных
        int length = Math.min(data.length, transmitBuffer.length);
        System.arraycopy(data, 0, transmitBuffer, 0, length);
        transmitLength = length;
        
        // Запуск передачи
        startCondition();
        
        // Отправка адреса с битом записи
        int fnAddress = 0x68 << 1; // Адрес ФН + бит записи
        if (!writeByte(fnAddress)) {
            return false;
        }
        
        // Отправка данных
        for (int i = 0; i < transmitLength; i++) {
            if (!writeByte(transmitBuffer[i] & 0xFF)) {
                return false;
            }
        }
        
        stopCondition();
        return true;
    }
    
    /**
     * Чтение данных из ФН
     */
    public byte[] readFNData(int length) {
        if (!enabled || fnDevice == null || !fnDevice.isConnected()) {
            return new byte[0];
        }
        
        byte[] result = new byte[length];
        
        // Запуск приема
        startCondition();
        
        // Отправка адреса с битом чтения
        int fnAddress = (0x68 << 1) | 0x01; // Адрес ФН + бит чтения
        if (!writeByte(fnAddress)) {
            return new byte[0];
        }
        
        // Чтение данных
        for (int i = 0; i < length; i++) {
            boolean sendAck = (i < length - 1); // ACK для всех кроме последнего байта
            result[i] = (byte) readByte(sendAck);
        }
        
        stopCondition();
        return result;
    }
    
    /**
     * Обновление регистра состояния
     */
    private void updateStatusRegister() {
        i2cStat = currentState;
        
        // Установка дополнительных флагов
        if (startSent) {
            i2cStat |= 0x20;
        }
        if (stopSent) {
            i2cStat |= 0x10;
        }
        if (arbitrationLost) {
            i2cStat |= 0x38;
        }
    }
    
    /**
     * Обработка прерывания I2C
     */
    public void handleInterrupt() {
        if (!enabled) {
            return;
        }
        
        logger.debug("I2C прерывание, состояние: 0x{}", 
                    Integer.toHexString(currentState));
        
        switch (currentState) {
            case 0x08: // START sent
                // Ожидание отправки адреса
                break;
                
            case 0x10: // Repeated START sent
                // Ожидание отправки адреса
                break;
                
            case 0x18: // SLA+W sent, ACK received
                // Готов к передаче данных
                break;
                
            case 0x28: // Data sent, ACK received
                // Готов к передаче следующих данных
                break;
                
            case 0x40: // SLA+R sent, ACK received
                // Готов к приему данных
                break;
                
            case 0x50: // Data received, ACK sent
                // Готов к приему следующих данных
                break;
                
            case 0x58: // Data received, NACK sent
                // Завершение приема
                break;
                
            case 0xF8: // Idle
                // Ничего не делать
                break;
                
            default:
                logger.warn("Необработанное состояние I2C: 0x{}", 
                           Integer.toHexString(currentState));
                break;
        }
    }
    
    // Getters для доступа к регистрам
    public int getI2cConset() {
        return i2cConset;
    }
    
    public int getI2cConclr() {
        return i2cConclr;
    }
    
    public int getI2cStat() {
        return i2cStat;
    }
    
    public int getI2cDat() {
        return i2cDat;
    }
    
    public int getI2cAdr() {
        return i2cAdr;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isMasterMode() {
        return masterMode;
    }
    
    public int getCurrentState() {
        return currentState;
    }
    
    public boolean isAckReceived() {
        return ackReceived;
    }
}
