package com.emulator.fphardware.controllers;

/**
 * Векторы прерываний эмулятора
 */
public class InterruptVector {
    public static final int TIMER = 0x00;
    public static final int PRINTER = 0x01;
    public static final int FISCAL_STORAGE = 0x02;
    public static final int SENSOR = 0x03;
    public static final int POWER = 0x04;
    public static final int CASE_OPEN = 0x05;
    public static final int PAPER_OUT = 0x06;
    public static final int PAPER_END = 0x07;
    public static final int FN_DISCONNECT = 0x08;
    public static final int KEYBOARD = 0x09;
    public static final int COMMUNICATION = 0x0A;
}
