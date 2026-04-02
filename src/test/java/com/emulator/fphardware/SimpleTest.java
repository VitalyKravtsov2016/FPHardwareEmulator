package com.emulator.fphardware;

import com.emulator.fphardware.memory.MemoryManager;

/**
 * Простой тест для проверки работы
 */
public class SimpleTest {
    
    public static void main(String[] args) {
        MemoryManager memory = new MemoryManager();
        
        System.out.println("ROM size: " + memory.getRomSize());
        System.out.println("RAM size: " + memory.getRamSize());
        System.out.println("I/O size: " + memory.getIoSize());
        
        // Тест записи в RAM
        memory.writeByte(0x8000, 0xAA);
        int value = memory.readByte(0x8000);
        System.out.println("Written 0xAA, read: 0x" + Integer.toHexString(value));
        
        // Тест записи в I/O
        memory.writeByte(0xFF00, 0x42);
        value = memory.readByte(0xFF00);
        System.out.println("Written 0x42, read: 0x" + Integer.toHexString(value));
        
        // Тест неверного адреса
        value = memory.readByte(0x7FFF);
        System.out.println("Read invalid address 0x7FFF: 0x" + Integer.toHexString(value));
    }
}
