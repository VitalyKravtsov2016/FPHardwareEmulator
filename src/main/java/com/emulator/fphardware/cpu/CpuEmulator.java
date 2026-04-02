package com.emulator.fphardware.cpu;

import com.emulator.fphardware.memory.MemoryManager;
import com.emulator.fphardware.controllers.ControllerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Эмулятор процессора фискального регистратора
 * Базовая реализация с поддержкой основных команд
 */
public class CpuEmulator {
    
    private static final Logger logger = LoggerFactory.getLogger(CpuEmulator.class);
    
    // Регистры процессора
    private int[] registers = new int[16]; // R0-R15
    private int programCounter = 0;        // Счетчик команд
    private int stackPointer = 0xFFFF;     // Указатель стека
    private int accumulator = 0;           // Аккумулятор
    private boolean carryFlag = false;     // Флаг переноса
    private boolean zeroFlag = false;      // Флаг нуля
    private boolean interruptFlag = true;   // Флаг разрешения прерываний
    
    // Состояние процессора
    private boolean running = false;
    private boolean halted = false;
    
    // Компоненты
    private final MemoryManager memoryManager;
    private final ControllerManager controllerManager;
    
    // Частота процессора (Гц)
    private static final int CPU_FREQUENCY = 12_000_000; // 12 МГц
    private long lastCycleTime = 0;
    
    public CpuEmulator(MemoryManager memoryManager, ControllerManager controllerManager) {
        this.memoryManager = memoryManager;
        this.controllerManager = controllerManager;
        reset();
    }
    
    /**
     * Сброс процессора в начальное состояние
     */
    public void reset() {
        logger.info("Сброс процессора");
        
        // Сброс регистров
        for (int i = 0; i < registers.length; i++) {
            registers[i] = 0;
        }
        
        programCounter = 0;
        stackPointer = 0xFFFF;
        accumulator = 0;
        carryFlag = false;
        zeroFlag = false;
        interruptFlag = true;
        
        running = false;
        halted = false;
        lastCycleTime = System.currentTimeMillis();
    }
    
    /**
     * Запуск процессора
     */
    public void start() {
        if (!running && !halted) {
            running = true;
            logger.info("Процессор запущен");
        }
    }
    
    /**
     * Остановка процессора
     */
    public void stop() {
        running = false;
        logger.info("Процессор остановлен");
    }
    
    /**
     * Выполнение одного цикла процессора
     */
    public void step() {
        if (!running || halted) {
            return;
        }
        
        try {
            // Чтение следующей команды
            int opcode = memoryManager.readByte(programCounter);
            executeInstruction(opcode);
            
            // Обработка прерываний
            if (interruptFlag) {
                checkInterrupts();
            }
            
            // Контроль частоты
            controlFrequency();
            
        } catch (Exception e) {
            logger.error("Ошибка выполнения команды по адресу 0x" + 
                        Integer.toHexString(programCounter) + ": " + e.getMessage());
            halt();
        }
    }
    
    /**
     * Выполнение инструкции
     */
    private void executeInstruction(int opcode) {
        // Базовая реализация - будет расширена
        switch (opcode & 0xFF) {
            case 0x00: // NOP
                programCounter++;
                break;
                
            case 0x01: // HALT
                halted = true;
                running = false;
                programCounter++;
                logger.info("Процессор остановлен командой HALT");
                break;
                
            case 0x02: // JMP addr
                int address = memoryManager.readWord(programCounter + 1);
                programCounter = address;
                break;
                
            case 0x10: // MOV R, data
                int reg = (opcode >> 4) & 0x0F;
                int data = memoryManager.readByte(programCounter + 1);
                registers[reg] = data & 0xFF;
                updateZeroFlag(registers[reg]);
                programCounter += 2;
                break;
                
            case 0x20: // ADD R, data
                reg = (opcode >> 4) & 0x0F;
                data = memoryManager.readByte(programCounter + 1);
                int result = registers[reg] + (data & 0xFF);
                carryFlag = (result & 0xFF00) != 0;
                registers[reg] = result & 0xFF;
                updateZeroFlag(registers[reg]);
                programCounter += 2;
                break;
                
            default:
                logger.warn("Неизвестная команда: 0x" + Integer.toHexString(opcode));
                programCounter++;
                break;
        }
    }
    
    /**
     * Проверка прерываний
     */
    private void checkInterrupts() {
        // Проверка прерываний от контроллеров
        if (controllerManager.hasPendingInterrupts()) {
            int interruptVector = controllerManager.getNextInterrupt();
            if (interruptVector != -1) {
                handleInterrupt(interruptVector);
            }
        }
    }
    
    /**
     * Обработка прерывания
     */
    private void handleInterrupt(int interruptVector) {
        logger.debug("Обработка прерывания: " + interruptVector);
        
        // Сохранение контекста
        memoryManager.writeWord(stackPointer, programCounter);
        stackPointer -= 2;
        
        // Переход к обработчику прерывания
        programCounter = memoryManager.readWord(interruptVector * 2);
        
        // Запрет прерываний
        interruptFlag = false;
    }
    
    /**
     * Контроль частоты процессора
     */
    private void controlFrequency() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastCycleTime;
        
        // Задержка для поддержания частоты
        long cycleTime = 1000 / (CPU_FREQUENCY / 1000); // время одного цикла в мс
        if (elapsed < cycleTime) {
            try {
                Thread.sleep(cycleTime - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        lastCycleTime = System.currentTimeMillis();
    }
    
    /**
     * Обновление флага нуля
     */
    private void updateZeroFlag(int value) {
        zeroFlag = (value & 0xFF) == 0;
    }
    
    /**
     * Аварийная остановка
     */
    public void halt() {
        halted = true;
        running = false;
        logger.error("Процессор остановлен из-за ошибки");
    }
    
    // Getters и setters
    public boolean isRunning() {
        return running;
    }
    
    public boolean isHalted() {
        return halted;
    }
    
    public int getProgramCounter() {
        return programCounter;
    }
    
    public int getAccumulator() {
        return accumulator;
    }
    
    public int[] getRegisters() {
        return registers.clone();
    }
    
    public boolean isCarryFlag() {
        return carryFlag;
    }
    
    public boolean isZeroFlag() {
        return zeroFlag;
    }
    
    public boolean isInterruptFlag() {
        return interruptFlag;
    }
}
