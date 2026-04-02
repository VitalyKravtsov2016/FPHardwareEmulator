package com.emulator.fphardware.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Менеджер памяти эмулятора фискального регистратора
 * Управляет ROM, RAM и памятью ввода-вывода
 */
public class MemoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(MemoryManager.class);
    
    // Размеры памяти (в байтах) - LPC1778FBD208
    private static final int ROM_SIZE = 512 * 1024;      // 512KB Flash
    private static final int RAM_SIZE = 64 * 1024;       // 64KB SRAM  
    private static final int IO_SIZE = 256;              // 256 байт I/O
    
    // Адресные пространства - LPC1778FBD208 (Cortex-M3)
    private static final int FLASH_START = 0x00000000;
    private static final int FLASH_END = FLASH_START + ROM_SIZE - 1;
    
    private static final int RAM_START = 0x20000000;
    private static final int RAM_END = RAM_START + RAM_SIZE - 1;
    
    private static final int PERIPHERAL_START = 0x40000000;
    private static final int PERIPHERAL_END = 0x400FFFFF; // 1MB периферии
    
    private static final int PRIVATE_PERIPHERAL_START = 0xE0000000;
    private static final int PRIVATE_PERIPHERAL_END = 0xE00FFFFF; // Private peripheral bus
    
    // Массивы памяти
    private final byte[] romMemory;
    private final byte[] ramMemory;
    private final byte[] ioMemory;
    
    // Флаг защиты ROM
    private boolean romProtected = true;
    
    public MemoryManager() {
        logger.info("Инициализация менеджера памяти");
        
        romMemory = new byte[ROM_SIZE];
        ramMemory = new byte[RAM_SIZE];
        ioMemory = new byte[IO_SIZE];
        
        // Инициализация RAM нулями
        for (int i = 0; i < RAM_SIZE; i++) {
            ramMemory[i] = 0;
        }
        
        // Инициализация I/O памяти нулями
        for (int i = 0; i < IO_SIZE; i++) {
            ioMemory[i] = 0;
        }
        
        logger.info("Память инициализирована: ROM={}KB, RAM={}KB, I/O={}B", 
                   ROM_SIZE / 1024, RAM_SIZE / 1024, IO_SIZE);
    }
    
    /**
     * Чтение байта из памяти
     */
    public int readByte(int address) {
        if (address >= FLASH_START && address <= FLASH_END) {
            return romMemory[address - FLASH_START] & 0xFF;
        } else if (address >= RAM_START && address <= RAM_END) {
            return ramMemory[address - RAM_START] & 0xFF;
        } else if (address >= PERIPHERAL_START && address <= PERIPHERAL_END) {
            return ioMemory[(address - PERIPHERAL_START) & 0xFF] & 0xFF;
        } else {
            logger.warn("Обращение по недопустимому адресу: 0x" + Integer.toHexString(address));
            return 0xFF; // Значение по умолчанию для неверных адресов
        }
    }
    
    /**
     * Запись байта в память
     */
    public void writeByte(int address, int value) {
        value &= 0xFF; // Обрезаем до байта
        
        if (address >= FLASH_START && address <= FLASH_END) {
            if (!romProtected) {
                romMemory[address - FLASH_START] = (byte) value;
            } else {
                logger.warn("Попытка записи в защищенную Flash по адресу: 0x" + 
                           Integer.toHexString(address));
            }
        } else if (address >= RAM_START && address <= RAM_END) {
            ramMemory[address - RAM_START] = (byte) value;
        } else if (address >= PERIPHERAL_START && address <= PERIPHERAL_END) {
            ioMemory[(address - PERIPHERAL_START) & 0xFF] = (byte) value;
            // Здесь может быть вызов обработчика I/O
            handleIOWrite(address, value);
        } else {
            logger.warn("Запись по недопустимому адресу: 0x" + Integer.toHexString(address));
        }
    }
    
    /**
     * Чтение слова (2 байта, little-endian)
     */
    public int readWord(int address) {
        int low = readByte(address);
        int high = readByte(address + 1);
        return (high << 8) | low;
    }
    
    /**
     * Запись слова (2 байта, little-endian)
     */
    public void writeWord(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }
    
    /**
     * Загрузка прошивки в ROM
     */
    public void loadFirmware(byte[] firmware) {
        if (firmware == null) {
            logger.error("Попытка загрузить пустую прошивку");
            return;
        }
        
        int loadSize = Math.min(firmware.length, ROM_SIZE);
        
        // Временно снимаем защиту ROM
        boolean oldProtection = romProtected;
        romProtected = false;
        
        try {
            // Копируем прошивку в ROM
            for (int i = 0; i < loadSize; i++) {
                romMemory[i] = firmware[i];
            }
            
            logger.info("Прошивка загружена: {} байт в ROM", loadSize);
        } finally {
            // Восстанавливаем защиту ROM
            romProtected = oldProtection;
        }
    }
    
    /**
     * Очистка RAM
     */
    public void clearRAM() {
        for (int i = 0; i < RAM_SIZE; i++) {
            ramMemory[i] = 0;
        }
        logger.info("RAM очищена");
    }
    
    /**
     * Обработка записи в порт ввода-вывода
     */
    private void handleIOWrite(int address, int value) {
        int port = (address - PERIPHERAL_START) & 0xFF;
        
        switch (port) {
            case 0x00: // Порт управления принтером
                logger.debug("Запись в порт принтера: 0x" + Integer.toHexString(value));
                break;
                
            case 0x01: // Порт управления ФН
                logger.debug("Запись в порт ФН: 0x" + Integer.toHexString(value));
                break;
                
            case 0x02: // Порт датчиков
                logger.debug("Запись в порт датчиков: 0x" + Integer.toHexString(value));
                break;
                
            default:
                logger.debug("Запись в I/O порт 0x" + Integer.toHexString(port) + 
                           ": 0x" + Integer.toHexString(value));
                break;
        }
    }
    
    /**
     * Установка/снятие защиты ROM
     */
    public void setRomProtection(boolean protected_) {
        this.romProtected = protected_;
        logger.info("Защита ROM: " + (protected_ ? "включена" : "выключена"));
    }
    
    /**
     * Получение дампа памяти
     */
    public byte[] getMemoryDump(int startAddress, int size) {
        byte[] dump = new byte[size];
        
        for (int i = 0; i < size; i++) {
            int address = startAddress + i;
            dump[i] = (byte) readByte(address);
        }
        
        return dump;
    }
    
    /**
     * Проверка допустимого адреса
     */
    public boolean isValidAddress(int address) {
        return (address >= FLASH_START && address <= FLASH_END) ||
               (address >= RAM_START && address <= RAM_END) ||
               (address >= PERIPHERAL_START && address <= PERIPHERAL_END) ||
               (address >= PRIVATE_PERIPHERAL_START && address <= PRIVATE_PERIPHERAL_END);
    }
    
    // Getters
    public int getRomSize() {
        return ROM_SIZE;
    }
    
    public int getRamSize() {
        return RAM_SIZE;
    }
    
    public int getIoSize() {
        return IO_SIZE;
    }
    
    public boolean isRomProtected() {
        return romProtected;
    }
}
