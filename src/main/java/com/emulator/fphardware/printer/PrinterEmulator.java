package com.emulator.fphardware.printer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Эмулятор термопринтера фискального регистратора
 */
public class PrinterEmulator {
    
    private static final Logger logger = LoggerFactory.getLogger(PrinterEmulator.class);
    
    // Параметры принтера
    private static final int LINE_WIDTH = 32; // символов в строке
    private static final int BUFFER_SIZE = 256; // размер буфера
    
    // Состояние принтера
    private boolean powerOn = false;
    private boolean paperPresent = true;
    private boolean printing = false;
    private boolean bufferFull = false;
    private boolean error = false;
    
    // Буфер данных
    private final List<String> printBuffer = new ArrayList<>();
    private final StringBuilder currentLine = new StringBuilder();
    
    // Регистры управления
    private int controlRegister = 0;
    private int statusRegister = 0;
    
    // Флаг прерывания
    private boolean interruptRequested = false;
    
    public PrinterEmulator() {
        logger.debug("Эмулятор принтера инициализирован");
        reset();
    }
    
    /**
     * Обновление состояния принтера
     */
    public void update() {
        if (!powerOn) {
            return;
        }
        
        // Обработка буфера печати
        processPrintBuffer();
        
        // Обновление статуса
        updateStatus();
    }
    
    /**
     * Обработка буфера печати
     */
    private void processPrintBuffer() {
        if (!printing || printBuffer.isEmpty()) {
            return;
        }
        
        // Имитация печати (в реальности здесь была бы задержка)
        try {
            Thread.sleep(100); // Задержка для имитации скорости печати
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Печатаем первую строку из буфера
        String line = printBuffer.remove(0);
        logger.info("Печать: " + line);
        
        if (printBuffer.isEmpty()) {
            printing = false;
            interruptRequested = true; // Прерывание завершения печати
        }
        
        bufferFull = printBuffer.size() >= BUFFER_SIZE;
    }
    
    /**
     * Запись данных в принтер
     */
    public void writeData(int data) {
        if (!powerOn || !paperPresent) {
            error = true;
            return;
        }
        
        char ch = (char) (data & 0xFF);
        
        if (ch == '\n' || ch == '\r') {
            // Конец строки - добавляем в буфер печати
            if (currentLine.length() > 0) {
                printBuffer.add(currentLine.toString());
                currentLine.setLength(0);
                printing = true;
            }
        } else if (ch >= 32 && ch <= 126) {
            // Печатаемый символ
            if (currentLine.length() < LINE_WIDTH) {
                currentLine.append(ch);
            } else {
                // Строка переполнена - добавляем в буфер и начинаем новую
                printBuffer.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(ch);
                printing = true;
            }
        } else if (ch == 0x0C) {
            // Form Feed - перевод страницы
            if (currentLine.length() > 0) {
                printBuffer.add(currentLine.toString());
                currentLine.setLength(0);
            }
            printBuffer.add(""); // Пустая строка для перевода страницы
            printing = true;
        }
        
        bufferFull = printBuffer.size() >= BUFFER_SIZE;
    }
    
    /**
     * Запись управляющего регистра
     */
    public void writeControl(int value) {
        controlRegister = value & 0xFF;
        
        // Бит 0 - сброс принтера
        if ((value & 0x01) != 0) {
            reset();
        }
        
        // Бит 1 - инициализация принтера
        if ((value & 0x02) != 0) {
            initialize();
        }
        
        logger.debug("Запись в управляющий регистр принтера: 0x" + 
                    Integer.toHexString(controlRegister));
    }
    
    /**
     * Чтение статуса принтера
     */
    public int readStatus() {
        updateStatus();
        return statusRegister;
    }
    
    /**
     * Чтение данных из принтера (если поддерживается)
     */
    public int readData() {
        // В данном эмуляторе чтение данных не поддерживается
        return 0xFF;
    }
    
    /**
     * Обновление регистра статуса
     */
    private void updateStatus() {
        statusRegister = 0;
        
        // Бит 0 - принтер готов
        if (powerOn && !error && !printing) {
            statusRegister |= 0x01;
        }
        
        // Бит 1 - бумага присутствует
        if (paperPresent) {
            statusRegister |= 0x02;
        }
        
        // Бит 2 - идет печать
        if (printing) {
            statusRegister |= 0x04;
        }
        
        // Бит 3 - буфер полон
        if (bufferFull) {
            statusRegister |= 0x08;
        }
        
        // Бит 7 - ошибка
        if (error) {
            statusRegister |= 0x80;
        }
    }
    
    /**
     * Инициализация принтера
     */
    private void initialize() {
        printBuffer.clear();
        currentLine.setLength(0);
        printing = false;
        bufferFull = false;
        error = false;
        interruptRequested = false;
        
        logger.debug("Принтер инициализирован");
    }
    
    /**
     * Сброс принтера
     */
    public void reset() {
        powerOn = false;
        paperPresent = true;
        printing = false;
        bufferFull = false;
        error = false;
        
        printBuffer.clear();
        currentLine.setLength(0);
        controlRegister = 0;
        statusRegister = 0;
        interruptRequested = false;
        
        logger.debug("Принтер сброшен");
    }
    
    /**
     * Проверка наличия прерывания
     */
    public boolean hasInterrupt() {
        return interruptRequested;
    }
    
    /**
     * Получение напечатанного текста
     */
    public List<String> getPrintedText() {
        return new ArrayList<>(printBuffer);
    }
    
    /**
     * Очистка буфера печати
     */
    public void clearBuffer() {
        printBuffer.clear();
        currentLine.setLength(0);
        printing = false;
        bufferFull = false;
        logger.debug("Буфер принтера очищен");
    }
    
    // Setters
    public void setPowerOn(boolean powerOn) {
        this.powerOn = powerOn;
        if (!powerOn) {
            reset();
        }
    }
    
    public void setPaperPresent(boolean paperPresent) {
        this.paperPresent = paperPresent;
        if (!paperPresent && printing) {
            error = true;
            interruptRequested = true;
        }
    }
    
    // Getters
    public boolean isPowerOn() {
        return powerOn;
    }
    
    public boolean isPaperPresent() {
        return paperPresent;
    }
    
    public boolean isPrinting() {
        return printing;
    }
    
    public boolean isError() {
        return error;
    }
    
    public boolean isBufferFull() {
        return bufferFull;
    }
    
    public int getBufferSize() {
        return printBuffer.size();
    }
}
