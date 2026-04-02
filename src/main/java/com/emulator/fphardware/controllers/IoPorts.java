package com.emulator.fphardware.controllers;

/**
 * Определения портов ввода-вывода
 */
public class IoPorts {
    // Порты принтера
    public static final int PRINTER_CONTROL = 0x00;
    public static final int PRINTER_STATUS = 0x01;
    public static final int PRINTER_DATA = 0x02;
    
    // Порты фискального накопителя
    public static final int FN_CONTROL = 0x10;
    public static final int FN_STATUS = 0x11;
    public static final int FN_DATA = 0x12;
    
    // Порты датчиков
    public static final int SENSOR_STATUS = 0x20;
    public static final int SENSOR_CONTROL = 0x21;
    
    // Порты питания
    public static final int POWER_STATUS = 0x30;
    public static final int POWER_CONTROL = 0x31;
    
    // Порты связи
    public static final int UART_DATA = 0x40;
    public static final int UART_STATUS = 0x41;
    public static final int UART_CONTROL = 0x42;
    
    // Порты клавиатуры
    public static final int KEYBOARD_DATA = 0x50;
    public static final int KEYBOARD_STATUS = 0x51;
}
