package com.emulator.fphardware.fn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Эмулятор фискального накопителя (ФН)
 * Подключается по I2C к основному процессору LPC1778FBD208
 * Реализует протокол обмена согласно ККТ_ФН_v1.2.06.pdf
 */
public class FiscalStorageEmulator {
    
    private static final Logger logger = LoggerFactory.getLogger(FiscalStorageEmulator.class);
    
    // I2C адрес ФН (обычно 0x68 или 0x69)
    private static final int FN_I2C_ADDRESS = 0x68;
    
    // Состояние ФН
    private boolean connected = false;
    private boolean powered = false;
    private boolean initialized = false;
    private boolean error = false;
    private boolean busy = false;
    
    // Буфер для приема/передачи данных
    private byte[] receiveBuffer = new byte[256];
    private byte[] transmitBuffer = new byte[256];
    private int receiveLength = 0;
    private int transmitLength = 0;
    private int transmitIndex = 0;
    
    // Регистры состояния ФН
    private int fnStatus = 0x00; // Статус ФН
    private int fnError = 0x00;  // Код ошибки
    
    // Фискальные данные
    private long fiscalCounter = 0;
    private long documentCounter = 0;
    private String fnNumber = "12345678901234567890"; // Номер ФН
    
    // I2C состояние
    private boolean i2cStartReceived = false;
    private boolean i2cStopReceived = false;
    private int i2cState = 0; // 0 - idle, 1 - address, 2 - data
    
    public FiscalStorageEmulator() {
        logger.info("Инициализация эмулятора ФН");
        reset();
    }
    
    /**
     * Сброс эмулятора ФН
     */
    public void reset() {
        logger.info("Сброс эмулятора ФН");
        
        connected = false;
        powered = false;
        initialized = false;
        error = false;
        busy = false;
        
        receiveLength = 0;
        transmitLength = 0;
        transmitIndex = 0;
        
        fnStatus = 0x00;
        fnError = 0x00;
        
        fiscalCounter = 0;
        documentCounter = 0;
        
        i2cStartReceived = false;
        i2cStopReceived = false;
        i2cState = 0;
        
        // Очистка буферов
        for (int i = 0; i < 256; i++) {
            receiveBuffer[i] = 0;
            transmitBuffer[i] = 0;
        }
    }
    
    /**
     * Подключение/отключение ФН
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
        logger.info("ФН подключен: {}", connected);
        
        if (connected && powered) {
            initialize();
        } else {
            initialized = false;
        }
    }
    
    /**
     * Управление питанием ФН
     */
    public void setPowered(boolean powered) {
        this.powered = powered;
        logger.info("Питание ФН: {}", powered);
        
        if (powered && connected) {
            initialize();
        } else {
            initialized = false;
        }
    }
    
    /**
     * Инициализация ФН
     */
    private void initialize() {
        logger.info("Инициализация ФН...");
        
        // Имитация инициализации
        try {
            Thread.sleep(100); // Задержка инициализации
            initialized = true;
            fnStatus = 0x01; // Готов к работе
            logger.info("ФН инициализирован");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = true;
            fnError = 0x01; // Ошибка инициализации
        }
    }
    
    /**
     * Обработка I2C START условия
     */
    public void i2cStartCondition() {
        if (!connected || !powered) {
            return;
        }
        
        i2cStartReceived = true;
        i2cStopReceived = false;
        i2cState = 1; // Ожидание адреса
        transmitIndex = 0;
        
        logger.debug("I2C START получен");
    }
    
    /**
     * Обработка I2C STOP условия
     */
    public void i2cStopCondition() {
        if (!connected || !powered) {
            return;
        }
        
        i2cStopReceived = true;
        i2cState = 0; // Idle
        
        // Обработка принятой команды
        if (receiveLength > 0) {
            processCommand();
        }
        
        logger.debug("I2C STOP получен, обработка команды");
    }
    
    /**
     * Обработка I2C байта (адрес или данные)
     */
    public boolean i2cWriteByte(int data) {
        if (!connected || !powered || !i2cStartReceived) {
            return false;
        }
        
        data &= 0xFF;
        
        switch (i2cState) {
            case 1: // Ожидание адреса
                if ((data & 0xFE) == (FN_I2C_ADDRESS << 1)) {
                    boolean readWrite = (data & 0x01) != 0;
                    i2cState = 2; // Ожидание данных
                    
                    if (readWrite) {
                        // Операция чтения - готовим ответ
                        prepareResponse();
                    }
                    
                    logger.debug("I2C адрес принят: 0x{}, RW: {}", 
                               Integer.toHexString(data), readWrite ? "R" : "W");
                    return true;
                } else {
                    // Не наш адрес
                    i2cState = 0;
                    return false;
                }
                
            case 2: // Прием данных
                if (receiveLength < receiveBuffer.length) {
                    receiveBuffer[receiveLength++] = (byte) data;
                    logger.debug("I2C данные приняты: 0x{}", Integer.toHexString(data));
                    return true;
                }
                break;
        }
        
        return false;
    }
    
    /**
     * Чтение байта по I2C
     */
    public int i2cReadByte() {
        if (!connected || !powered || !i2cStartReceived || transmitIndex >= transmitLength) {
            return 0xFF; // NACK
        }
        
        int data = transmitBuffer[transmitIndex++] & 0xFF;
        
        logger.debug("I2C данные отправлены: 0x{}", Integer.toHexString(data));
        
        return data;
    }
    
    /**
     * Проверка ACK/NACK
     */
    public boolean i2cNeedsAck() {
        return transmitIndex < transmitLength;
    }
    
    /**
     * Обработка принятой команды
     */
    private void processCommand() {
        if (receiveLength == 0) {
            return;
        }
        
        int command = receiveBuffer[0] & 0xFF;
        logger.info("Обработка команды ФН: 0x{}", Integer.toHexString(command));
        
        busy = true;
        
        try {
            switch (command) {
                case 0x01: // GET_STATUS
                    processGetStatus();
                    break;
                    
                case 0x02: // OPEN_CHECK
                    processOpenCheck();
                    break;
                    
                case 0x03: // CLOSE_CHECK
                    processCloseCheck();
                    break;
                    
                case 0x04: // GET_FN_NUMBER
                    processGetFnNumber();
                    break;
                    
                case 0x05: // GET_FISCAL_COUNTER
                    processGetFiscalCounter();
                    break;
                    
                default:
                    logger.warn("Неизвестная команда ФН: 0x{}", Integer.toHexString(command));
                    fnError = 0xFF; // Неизвестная команда
                    break;
            }
        } catch (Exception e) {
            logger.error("Ошибка обработки команды ФН: {}", e.getMessage());
            error = true;
            fnError = 0x02; // Внутренняя ошибка
        } finally {
            busy = false;
            receiveLength = 0; // Очистка буфера приема
        }
    }
    
    /**
     * Команда GET_STATUS
     */
    private void processGetStatus() {
        transmitLength = 4;
        transmitBuffer[0] = 0x01; // Код ответа
        transmitBuffer[1] = (byte) fnStatus;
        transmitBuffer[2] = (byte) fnError;
        transmitBuffer[3] = 0x00; // Резерв
        
        logger.debug("GET_STATUS: статус={}, ошибка={}", fnStatus, fnError);
    }
    
    /**
     * Команда OPEN_CHECK
     */
    private void processOpenCheck() {
        documentCounter++;
        
        try {
            double sum = Double.parseDouble(parts[1]);
            int tax = Integer.parseInt(parts[2]);
            
            fiscalDocumentNumber++;
            
            logger.info("Чек закрыт. Сумма: " + sum + ", Налог: " + tax);
            sendResponse("CHECK_CLOSED:" + fiscalDocumentNumber + ":" + sum);
            
        } catch (NumberFormatException e) {
            sendResponse("ERROR:INVALID_FORMAT");
        }
    }
    
    /**
     * Обработка команды открытия смены
     */
    private void processOpenShift() {
        logger.info("Смена открыта");
        sendResponse("SHIFT_OPENED");
    }
    
    /**
     * Обработка команды закрытия смены
     */
    private void processCloseShift() {
        logger.info("Смена закрыта");
        sendResponse("SHIFT_CLOSED");
    }
    
    /**
     * Отправка ответа
     */
    private void sendResponse(String response) {
        logger.debug("Ответ ФН: " + response);
        
        // Добавляем байты ответа в буфер передачи
        transmitBuffer.add(0xFF); // Начало ответа
        for (int i = 0; i < response.length(); i++) {
            transmitBuffer.add((int) response.charAt(i));
        }
        transmitBuffer.add(0xFE); // Конец ответа
    }
    
    /**
     * Запись данных в ФН
     */
    public void writeData(int data) {
        if (!connected || !powerOn) {
            return;
        }
        
        receiveBuffer.add(data & 0xFF);
    }
    
    /**
     * Запись управляющего регистра
     */
    public void writeControl(int value) {
        controlRegister = value & 0xFF;
        
        // Бит 0 - сброс ФН
        if ((value & 0x01) != 0) {
            reset();
        }
        
        // Бит 1 - инициализация ФН
        if ((value & 0x02) != 0) {
            initializeFN();
        }
        
        logger.debug("Запись в управляющий регистр ФН: 0x" + 
                    Integer.toHexString(controlRegister));
    }
    
    /**
     * Чтение статуса ФН
     */
    public int readStatus() {
        updateStatus();
        return statusRegister;
    }
    
    /**
     * Чтение данных из ФН
     */
    public int readData() {
        if (transmitBuffer.isEmpty()) {
            return 0xFF;
        }
        
        return transmitBuffer.remove(0) & 0xFF;
    }
    
    /**
     * Обновление регистра статуса
     */
    private void updateStatus() {
        statusRegister = 0;
        
        // Бит 0 - ФН готов
        if (connected && powerOn && initialized && !error && !busy) {
            statusRegister |= 0x01;
        }
        
        // Бит 1 - ФН подключен
        if (connected) {
            statusRegister |= 0x02;
        }
        
        // Бит 2 - ФН занят
        if (busy) {
            statusRegister |= 0x04;
        }
        
        // Бит 3 - есть данные для чтения
        if (!transmitBuffer.isEmpty()) {
            statusRegister |= 0x08;
        }
        
        // Бит 7 - ошибка
        if (error) {
            statusRegister |= 0x80;
        }
    }
    
    /**
     * Сброс ФН
     */
    public void reset() {
        connected = true;
        powerOn = false;
        initialized = false;
        error = false;
        busy = false;
        
        receiveBuffer.clear();
        transmitBuffer.clear();
        commandBuffer.setLength(0);
        
        controlRegister = 0;
        statusRegister = 0;
        interruptRequested = false;
        
        logger.debug("ФН сброшен");
    }
    
    /**
     * Проверка наличия прерывания
     */
    public boolean hasInterrupt() {
        return interruptRequested;
    }
    
    // Setters
    public void setConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            reset();
        }
    }
    
    public void setPowerOn(boolean powerOn) {
        this.powerOn = powerOn;
        if (powerOn && !initialized) {
            initializeFN();
        }
    }
    
    // Getters
    public boolean isConnected() {
        return connected;
    }
    
    public boolean isPowerOn() {
        return powerOn;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public boolean isError() {
        return error;
    }
    
    public boolean isBusy() {
        return busy;
    }
    
    public int getDocumentCount() {
        return documentCount;
    }
    
    public int getFiscalDocumentNumber() {
        return fiscalDocumentNumber;
    }
    
    public String getSerialNumber() {
        return serialNumber;
    }
}
