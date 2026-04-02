package com.emulator.fphardware;

import com.emulator.fphardware.memory.MemoryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для менеджера памяти
 */
public class MemoryManagerTest {
    
    private MemoryManager memoryManager;
    
    @BeforeEach
    void setUp() {
        memoryManager = new MemoryManager();
    }
    
    @Test
    void testMemoryInitialization() {
        assertEquals(512 * 1024, memoryManager.getRomSize());
        assertEquals(64 * 1024, memoryManager.getRamSize());
        assertEquals(256, memoryManager.getIoSize());
        assertTrue(memoryManager.isRomProtected());
    }
    
    @Test
    void testRomReadWrite() {
        // Проверка чтения из ROM после инициализации
        int value = memoryManager.readByte(0x0000);
        assertEquals(0, value);
        
        // Проверка защиты ROM
        memoryManager.writeByte(0x0000, 0x55);
        value = memoryManager.readByte(0x0000);
        assertEquals(0, value); // Должно остаться 0 из-за защиты
        
        // Снятие защиты и запись
        memoryManager.setRomProtection(false);
        memoryManager.writeByte(0x0000, 0x55);
        value = memoryManager.readByte(0x0000);
        assertEquals(0x55, value);
    }
    
    @Test
    void testRamReadWrite() {
        // Запись в RAM (адреса Cortex-M3 начинаются с 0x20000000)
        memoryManager.writeByte(0x20000000, 0xAA);
        int value = memoryManager.readByte(0x20000000);
        assertEquals(0xAA, value);
        
        // Запись слова
        memoryManager.writeWord(0x20000000, 0x1234);
        int word = memoryManager.readWord(0x20000000);
        assertEquals(0x1234, word);
    }
    
    @Test
    void testIoPorts() {
        // Запись в порт (адреса периферии Cortex-M3 начинаются с 0x40000000)
        memoryManager.writeByte(0x40000000, 0x42);
        int value = memoryManager.readByte(0x40000000);
        assertEquals(0x42, value);
    }
    
    @Test
    void testInvalidAddresses() {
        // Чтение по неверному адресу должно возвращать 0xFF
        int value = memoryManager.readByte(0x10000000); // За пределами адресного пространства Cortex-M3
        assertEquals(0xFF, value);
        
        // Запись по неверному адресу не должна вызывать ошибку
        assertDoesNotThrow(() -> {
            memoryManager.writeByte(0x10000000, 0x55);
        });
    }
    
    @Test
    void testFirmwareLoading() {
        // Создание тестовой прошивки
        byte[] firmware = new byte[100];
        for (int i = 0; i < firmware.length; i++) {
            firmware[i] = (byte) (i & 0xFF);
        }
        
        // Загрузка прошивки
        memoryManager.loadFirmware(firmware);
        
        // Проверка загрузки
        for (int i = 0; i < firmware.length; i++) {
            int value = memoryManager.readByte(i);
            assertEquals(i & 0xFF, value);
        }
    }
    
    @Test
    void testMemoryDump() {
        // Запись тестовых данных в RAM
        memoryManager.writeByte(0x20000000, 0x11);
        memoryManager.writeByte(0x20000001, 0x22);
        memoryManager.writeByte(0x20000002, 0x33);
        
        // Получение дампа
        byte[] dump = memoryManager.getMemoryDump(0x20000000, 3);
        
        assertEquals(3, dump.length);
        assertEquals(0x11, dump[0] & 0xFF);
        assertEquals(0x22, dump[1] & 0xFF);
        assertEquals(0x33, dump[2] & 0xFF);
    }
}
