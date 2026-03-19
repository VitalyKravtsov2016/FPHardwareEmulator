package com.fpemulator.cpu;

import java.util.logging.Logger;

/**
 * Simplified ARM Cortex-M3 core emulation.
 *
 * Implements a subset of the Thumb-2 instruction set sufficient to run
 * typical bare-metal firmware for the LPC1778:
 *   – 16-bit Thumb instructions
 *   – Most common 32-bit Thumb-2 encodings
 *   – Cortex-M3 exception / NVIC model
 *
 * This is an interpretive emulator that decodes and executes one instruction
 * at a time, making it easy to add instrumentation and debugging features.
 */
public class CortexM3Core {

    private static final Logger LOG = Logger.getLogger(CortexM3Core.class.getName());

    private final Registers regs = new Registers();
    private final Memory memory;

    // Execution state
    private volatile boolean running = false;
    private volatile boolean halted  = false;
    private long instructionCount    = 0;

    // NVIC – simplified: 32 external IRQ lines, each with a priority byte
    private final boolean[] irqPending  = new boolean[32];
    private final boolean[] irqEnabled  = new boolean[32];
    private final int[]     irqPriority = new int[32];
    private int primaskI = 0;          // PRIMASK.PM – global interrupt disable

    // Breakpoints (set of PC addresses)
    private final java.util.Set<Integer> breakpoints = new java.util.HashSet<>();

    // Step listener (called after every instruction for tracing/UI updates)
    private StepListener stepListener;

    @FunctionalInterface
    public interface StepListener {
        void onStep(long count, int pc, int instruction);
    }

    public CortexM3Core(Memory memory) {
        this.memory = memory;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Reset the CPU: load SP from vector table[0] and PC from vector table[1].
     */
    public void reset() {
        regs.reset();
        halted = false;
        instructionCount = 0;
        // ARM Cortex-M3 reset: SP = Mem[0x00], PC = Mem[0x04] | 1 (Thumb)
        int sp = memory.readWord(0x00000000);
        int pc = memory.readWord(0x00000004) & ~1;
        regs.setSP(sp);
        regs.setPC(pc);
        LOG.fine(String.format("CPU reset: SP=0x%08X PC=0x%08X", sp, pc));
    }

    /** Execute a single instruction. Returns false if halted/breakpoint hit. */
    public boolean step() {
        if (halted) return false;

        int pc = regs.getPC();
        if (breakpoints.contains(pc)) {
            LOG.info(String.format("Breakpoint hit at 0x%08X", pc));
            halted = true;
            return false;
        }

        // Thumb instructions are 16 or 32 bit. Check the high 5 bits to detect 32-bit.
        int hw0 = memory.readHalfWord(pc) & 0xFFFF;
        boolean is32bit = (hw0 >> 11) >= 0x1D; // 11101xx, 11110xx, 11111xx

        int instruction;
        if (is32bit) {
            int hw1 = memory.readHalfWord(pc + 2) & 0xFFFF;
            instruction = (hw0 << 16) | hw1;
            regs.setPC(pc + 4);
            execute32(instruction, pc);
        } else {
            instruction = hw0;
            regs.setPC(pc + 2);
            execute16(instruction, pc);
        }

        instructionCount++;
        if (stepListener != null) {
            stepListener.onStep(instructionCount, pc, instruction);
        }

        // Check for pending interrupts
        if (primaskI == 0) {
            checkInterrupts();
        }

        return !halted;
    }

    /**
     * Run until halted, breakpoint, or the stop flag is cleared.
     * Should be called from a dedicated thread.
     */
    public void run() {
        running = true;
        while (running && !halted) {
            step();
        }
    }

    public void stop() { running = false; }
    public boolean isRunning() { return running && !halted; }
    public boolean isHalted()  { return halted; }
    public void resume() { halted = false; }

    // ── 16-bit Thumb instruction execution ───────────────────────────────────

    @SuppressWarnings("java:S3776") // Cognitive complexity – intentional decoder table
    private void execute16(int ins, int pc) {
        int op = (ins >> 10) & 0x3F;

        if ((ins & 0xFC00) == 0x0000 && (ins & 0x01C0) != 0x0180) {
            // LSL (immediate) Rd, Rm, #imm5
            int rd   = ins & 0x7;
            int rm   = (ins >> 3) & 0x7;
            int imm5 = (ins >> 6) & 0x1F;
            int result = regs.get(rm) << imm5;
            regs.set(rd, result);
            regs.updateNZ(result);
            if (imm5 > 0) regs.setC(((regs.get(rm) >>> (32 - imm5)) & 1) != 0);
        } else if ((ins & 0xFC00) == 0x0400) {
            // LSR (immediate) Rd, Rm, #imm5
            int rd   = ins & 0x7;
            int rm   = (ins >> 3) & 0x7;
            int imm5 = (ins >> 6) & 0x1F;
            if (imm5 == 0) imm5 = 32;
            int rm_val = regs.get(rm);
            regs.setC(imm5 == 32 ? (rm_val < 0) : ((rm_val >>> (imm5 - 1)) & 1) != 0);
            int result = (imm5 == 32) ? 0 : (rm_val >>> imm5);
            regs.set(rd, result);
            regs.updateNZ(result);
        } else if ((ins & 0xFC00) == 0x0800) {
            // ASR (immediate)
            int rd   = ins & 0x7;
            int rm   = (ins >> 3) & 0x7;
            int imm5 = (ins >> 6) & 0x1F;
            if (imm5 == 0) imm5 = 32;
            int rm_val = regs.get(rm);
            regs.setC(imm5 == 32 ? (rm_val < 0) : ((rm_val >> (imm5 - 1)) & 1) != 0);
            int result = rm_val >> imm5;
            regs.set(rd, result);
            regs.updateNZ(result);
        } else if ((ins & 0xFE00) == 0x1800) {
            // ADD (register) Rd, Rn, Rm
            int rd = ins & 0x7;
            int rn = (ins >> 3) & 0x7;
            int rm = (ins >> 6) & 0x7;
            long result32 = (regs.get(rn) & 0xFFFF_FFFFL) + (regs.get(rm) & 0xFFFF_FFFFL);
            regs.updateNZCV_Add(regs.get(rn), regs.get(rm), result32);
            regs.set(rd, (int) result32);
        } else if ((ins & 0xFE00) == 0x1A00) {
            // SUB (register) Rd, Rn, Rm
            int rd = ins & 0x7;
            int rn = (ins >> 3) & 0x7;
            int rm = (ins >> 6) & 0x7;
            regs.updateNZCV_Sub(regs.get(rn), regs.get(rm));
            regs.set(rd, regs.get(rn) - regs.get(rm));
        } else if ((ins & 0xFE00) == 0x1C00) {
            // ADD (immediate 3) Rd, Rn, #imm3
            int rd   = ins & 0x7;
            int rn   = (ins >> 3) & 0x7;
            int imm3 = (ins >> 6) & 0x7;
            long result32 = (regs.get(rn) & 0xFFFF_FFFFL) + imm3;
            regs.updateNZCV_Add(regs.get(rn), imm3, result32);
            regs.set(rd, (int) result32);
        } else if ((ins & 0xFE00) == 0x1E00) {
            // SUB (immediate 3) Rd, Rn, #imm3
            int rd   = ins & 0x7;
            int rn   = (ins >> 3) & 0x7;
            int imm3 = (ins >> 6) & 0x7;
            regs.updateNZCV_Sub(regs.get(rn), imm3);
            regs.set(rd, regs.get(rn) - imm3);
        } else if ((ins & 0xE000) == 0x2000) {
            // MOV / CMP / ADD / SUB (immediate 8) Rdn, #imm8
            int rdn  = (ins >> 8) & 0x7;
            int imm8 = ins & 0xFF;
            int subop = (ins >> 11) & 0x3;
            switch (subop) {
                case 0 -> { regs.set(rdn, imm8); regs.updateNZ(imm8); }       // MOV
                case 1 -> regs.updateNZCV_Sub(regs.get(rdn), imm8);            // CMP
                case 2 -> { long r = (regs.get(rdn)&0xFFFF_FFFFL)+imm8;
                             regs.updateNZCV_Add(regs.get(rdn), imm8, r);
                             regs.set(rdn, (int)r); }                           // ADD
                case 3 -> { regs.updateNZCV_Sub(regs.get(rdn), imm8);
                             regs.set(rdn, regs.get(rdn) - imm8); }             // SUB
            }
        } else if ((ins & 0xFC00) == 0x4000) {
            // Data-processing (ALU ops)
            executeDataProcessing16(ins);
        } else if ((ins & 0xFF00) == 0x4700) {
            // BX / BLX
            int rm = (ins >> 3) & 0xF;
            int target = regs.get(rm);
            if ((ins & 0x0080) != 0) { // BLX
                regs.setLR((regs.getPC()) | 1);
            }
            regs.setPC(target & ~1);
        } else if ((ins & 0xFF00) == 0x4600) {
            // MOV (register, high)
            int rd = (ins & 0x7) | ((ins >> 4) & 0x8);
            int rm = (ins >> 3) & 0xF;
            if (rd == 15) {
                regs.setPC(regs.get(rm) & ~1);
            } else {
                regs.set(rd, regs.get(rm));
            }
        } else if ((ins & 0xF800) == 0x4800) {
            // LDR (literal) Rt, [PC, #imm8*4]
            int rt   = (ins >> 8) & 0x7;
            int imm8 = ins & 0xFF;
            int addr = (regs.getPC() & ~3) + (imm8 << 2);
            regs.set(rt, memory.readWord(addr));
        } else if ((ins & 0xE000) == 0x6000) {
            // LDR/STR (immediate offset)
            executeLoadStore16(ins);
        } else if ((ins & 0xF000) == 0x8000) {
            // LDRH/STRH (immediate)
            executeLoadStoreHalf16(ins);
        } else if ((ins & 0xF000) == 0x9000) {
            // LDR/STR SP-relative
            int rt   = (ins >> 8) & 0x7;
            int imm8 = ins & 0xFF;
            int addr = regs.getSP() + (imm8 << 2);
            if ((ins & 0x0800) == 0) {
                memory.writeWord(addr, regs.get(rt)); // STR
            } else {
                regs.set(rt, memory.readWord(addr));  // LDR
            }
        } else if ((ins & 0xF000) == 0xA000) {
            // ADD (SP or PC + imm8*4) → Rd
            int rd   = (ins >> 8) & 0x7;
            int imm8 = ins & 0xFF;
            int base = ((ins & 0x0800) == 0) ? (regs.getPC() & ~3) : regs.getSP();
            regs.set(rd, base + (imm8 << 2));
        } else if ((ins & 0xFF00) == 0xB000) {
            // ADD/SUB SP, SP, #imm7*4
            int imm7 = ins & 0x7F;
            if ((ins & 0x0080) == 0) regs.setSP(regs.getSP() + (imm7 << 2));
            else                      regs.setSP(regs.getSP() - (imm7 << 2));
        } else if ((ins & 0xFE00) == 0xB400) {
            // PUSH
            executePushPop16(ins, true, false);
        } else if ((ins & 0xFE00) == 0xBC00) {
            // POP
            executePushPop16(ins, false, false);
        } else if ((ins & 0xFF00) == 0xBE00) {
            // BKPT – breakpoint
            halted = true;
        } else if ((ins & 0xF000) == 0xC000) {
            // STM / LDM
            executeLoadStoreMultiple16(ins);
        } else if ((ins & 0xFF00) == 0xDF00) {
            // SVC
            int imm8 = ins & 0xFF;
            handleSVC(imm8);
        } else if ((ins & 0xF000) == 0xD000) {
            // Conditional branch / SVC / UDF
            int cond = (ins >> 8) & 0xF;
            if (cond == 0xE || cond == 0xF) return; // UDF / SVC handled above
            int soff = (byte)(ins & 0xFF) << 1;      // sign-extend 8→9 bit offset
            if (conditionTrue(cond)) {
                regs.setPC(regs.getPC() + soff);
            }
        } else if ((ins & 0xF800) == 0xE000) {
            // B (unconditional) 11-bit offset
            int soff = ((ins & 0x3FF) << 1) | ((ins & 0x0400) != 0 ? 0xFFFFF800 : 0);
            regs.setPC(regs.getPC() + soff);
        }
        // Other Thumb-1 encodings are treated as NOP (not yet implemented)
    }

    private void executeDataProcessing16(int ins) {
        int op = (ins >> 6) & 0xF;
        int rm = (ins >> 3) & 0x7;
        int rd = ins & 0x7;
        int a  = regs.get(rd);
        int b  = regs.get(rm);
        int result;
        switch (op) {
            case 0x0 -> { result = a & b; regs.set(rd, result); regs.updateNZ(result); }          // AND
            case 0x1 -> { result = a ^ b; regs.set(rd, result); regs.updateNZ(result); }          // EOR
            case 0x2 -> { // LSL (register)
                int shift = b & 0xFF;
                if (shift > 0) regs.setC(shift <= 32 && ((a >>> (32 - shift)) & 1) != 0);
                result = shift >= 32 ? 0 : (a << shift);
                regs.set(rd, result); regs.updateNZ(result); }
            case 0x3 -> { // LSR (register)
                int shift = b & 0xFF;
                if (shift > 0) regs.setC(shift <= 32 && ((a >>> (shift - 1)) & 1) != 0);
                result = shift >= 32 ? 0 : (a >>> shift);
                regs.set(rd, result); regs.updateNZ(result); }
            case 0x4 -> { // ASR (register)
                int shift = b & 0xFF;
                result = shift >= 32 ? (a >> 31) : (a >> shift);
                regs.set(rd, result); regs.updateNZ(result); }
            case 0x5 -> { // ADC
                long r32 = (a & 0xFFFF_FFFFL) + (b & 0xFFFF_FFFFL) + (regs.isC() ? 1L : 0L);
                regs.updateNZCV_Add(a, b, r32); regs.set(rd, (int)r32); }
            case 0x6 -> { // SBC
                result = a - b - (regs.isC() ? 0 : 1);
                regs.updateNZCV_Sub(a, b); regs.set(rd, result); }
            case 0x7 -> { // ROR
                int shift = b & 0x1F;
                result = (a >>> shift) | (a << (32 - shift));
                regs.set(rd, result); regs.updateNZ(result); }
            case 0x8 -> regs.updateNZCV_Sub(a, b);                               // TST
            case 0x9 -> { result = -b; regs.updateNZCV_Sub(0, b); regs.set(rd, result); } // NEG
            case 0xA -> regs.updateNZCV_Sub(a, b);                               // CMP
            case 0xB -> { regs.updateNZCV_Add(a, b, (a&0xFFFF_FFFFL)+(b&0xFFFF_FFFFL));} // CMN
            case 0xC -> { result = a | b; regs.set(rd, result); regs.updateNZ(result); }  // ORR
            case 0xD -> { // MUL
                result = a * b;
                regs.set(rd, result); regs.updateNZ(result); }
            case 0xE -> { result = a & ~b; regs.set(rd, result); regs.updateNZ(result); } // BIC
            case 0xF -> { result = ~b; regs.set(rd, result); regs.updateNZ(result); }      // MVN
        }
    }

    private void executeLoadStore16(int ins) {
        int rd   = ins & 0x7;
        int rn   = (ins >> 3) & 0x7;
        int imm5 = (ins >> 6) & 0x1F;
        int op   = (ins >> 11) & 0x3;
        switch (op) {
            case 0 -> memory.writeWord(regs.get(rn) + (imm5 << 2), regs.get(rd));  // STR
            case 1 -> regs.set(rd, memory.readWord(regs.get(rn) + (imm5 << 2)));    // LDR
            case 2 -> memory.writeByte(regs.get(rn) + imm5, regs.get(rd));          // STRB
            case 3 -> regs.set(rd, memory.readByte(regs.get(rn) + imm5));           // LDRB
        }
    }

    private void executeLoadStoreHalf16(int ins) {
        int rd   = ins & 0x7;
        int rn   = (ins >> 3) & 0x7;
        int imm5 = (ins >> 6) & 0x1F;
        int addr = regs.get(rn) + (imm5 << 1);
        if ((ins & 0x0800) == 0) memory.writeHalfWord(addr, regs.get(rd)); // STRH
        else regs.set(rd, memory.readHalfWord(addr));                        // LDRH
    }

    private void executePushPop16(int ins, boolean push, boolean lr) {
        int regList = ins & 0xFF;
        boolean lrPc = (ins & 0x0100) != 0;
        if (push) {
            if (lrPc) { regs.setSP(regs.getSP() - 4); memory.writeWord(regs.getSP(), regs.getLR()); }
            for (int i = 7; i >= 0; i--) {
                if ((regList & (1 << i)) != 0) {
                    regs.setSP(regs.getSP() - 4);
                    memory.writeWord(regs.getSP(), regs.get(i));
                }
            }
        } else { // pop
            for (int i = 0; i <= 7; i++) {
                if ((regList & (1 << i)) != 0) {
                    regs.set(i, memory.readWord(regs.getSP()));
                    regs.setSP(regs.getSP() + 4);
                }
            }
            if (lrPc) {
                regs.setPC(memory.readWord(regs.getSP()) & ~1);
                regs.setSP(regs.getSP() + 4);
            }
        }
    }

    private void executeLoadStoreMultiple16(int ins) {
        int rn      = (ins >> 8) & 0x7;
        int regList = ins & 0xFF;
        int addr    = regs.get(rn);
        if ((ins & 0x0800) == 0) { // STMIA
            for (int i = 0; i <= 7; i++) {
                if ((regList & (1 << i)) != 0) {
                    memory.writeWord(addr, regs.get(i));
                    addr += 4;
                }
            }
            regs.set(rn, addr);
        } else { // LDMIA
            for (int i = 0; i <= 7; i++) {
                if ((regList & (1 << i)) != 0) {
                    regs.set(i, memory.readWord(addr));
                    addr += 4;
                }
            }
            regs.set(rn, addr);
        }
    }

    // ── 32-bit Thumb-2 instruction execution ─────────────────────────────────

    @SuppressWarnings("java:S3776")
    private void execute32(int ins, int pc) {
        int op1 = (ins >> 27) & 0x3;
        int op2 = (ins >> 20) & 0x7F;

        // BL / BLX (unconditional branch with link)
        if ((ins & 0xF800D000) == 0xF000D000 || (ins & 0xF800D000) == 0xF000C000) {
            // BL encoding T1
            int s    = (ins >> 26) & 1;
            int i1   = 1 ^ (((ins >> 13) & 1) ^ s);
            int i2   = 1 ^ (((ins >> 11) & 1) ^ s);
            int imm10 = (ins >> 16) & 0x3FF;
            int imm11 = ins & 0x7FF;
            int offset = (s << 24) | (i1 << 23) | (i2 << 22) | (imm10 << 12) | (imm11 << 1);
            if (s != 0) offset |= 0xFF000000; // sign-extend
            regs.setLR((regs.getPC()) | 1);
            regs.setPC(regs.getPC() + offset);
            return;
        }

        // MOV (immediate) wide  – T2/T3
        if ((ins & 0xFBF0_8000) == 0xF040_0000) {
            int rd   = (ins >> 8) & 0xF;
            int imm  = buildImm12(ins);
            regs.set(rd, imm);
            // optional S bit
            if ((ins & 0x0010_0000) != 0) regs.updateNZ(imm);
            return;
        }

        // MOVW T3 (16-bit zero-extended immediate into Rd)
        if ((ins & 0xFBF0_0000) == 0xF240_0000) {
            int rd  = (ins >> 8) & 0xF;
            int imm = ((ins >> 4) & 0xF000) | ((ins >> 15) << 11) | ((ins >> 16) & 0xFF) | ((ins & 0x700) >> 4) | (ins & 0xFF);
            regs.set(rd, imm & 0xFFFF);
            return;
        }

        // MOVT T1 – move top 16 bits
        if ((ins & 0xFBF0_0000) == 0xF2C0_0000) {
            int rd  = (ins >> 8) & 0xF;
            int imm = ((ins >> 4) & 0xF000) | ((ins >> 15) << 11) | ((ins >> 16) & 0xFF) | ((ins & 0x700) >> 4) | (ins & 0xFF);
            regs.set(rd, (regs.get(rd) & 0x0000_FFFF) | ((imm & 0xFFFF) << 16));
            return;
        }

        // LDR (immediate) T3 – 12-bit offset
        if ((ins & 0xFF70_0000) == 0xF850_0000 || (ins & 0xFFF0_0000) == 0xF8D0_0000) {
            int rt  = (ins >> 12) & 0xF;
            int rn  = (ins >> 16) & 0xF;
            int imm = ins & 0xFFF;
            regs.set(rt, memory.readWord(regs.get(rn) + imm));
            return;
        }

        // STR (immediate) T3 – 12-bit offset
        if ((ins & 0xFFF0_0000) == 0xF8C0_0000) {
            int rt  = (ins >> 12) & 0xF;
            int rn  = (ins >> 16) & 0xF;
            int imm = ins & 0xFFF;
            memory.writeWord(regs.get(rn) + imm, regs.get(rt));
            return;
        }

        // Other 32-bit instructions – treated as NOP for now
        LOG.finest(String.format("Unhandled 32-bit instruction 0x%08X at 0x%08X", ins, pc));
    }

    /** Decode the modified immediate constant (imm12) used in data-processing instructions. */
    private int buildImm12(int ins) {
        int i   = (ins >> 26) & 1;
        int imm3 = (ins >> 12) & 0x7;
        int abcdefgh = ins & 0xFF;
        int imm12 = (i << 11) | (imm3 << 8) | abcdefgh;
        // Thumb-2 modified immediate (ARMv7-M A5-13)
        if ((imm12 >> 10) == 0) {
            return switch ((imm12 >> 8) & 0x3) {
                case 0 -> abcdefgh;
                case 1 -> (abcdefgh << 16) | abcdefgh;
                case 2 -> (abcdefgh << 24) | (abcdefgh << 8);
                case 3 -> (abcdefgh << 24) | (abcdefgh << 16) | (abcdefgh << 8) | abcdefgh;
                default -> 0;
            };
        }
        // Rotate
        int value = 0x80 | (imm12 & 0x7F);
        int rot   = (imm12 >> 7) & 0x1F;
        return (value >>> rot) | (value << (32 - rot));
    }

    // ── Condition codes ───────────────────────────────────────────────────────

    private boolean conditionTrue(int cond) {
        return switch (cond) {
            case 0x0 -> regs.isZ();                              // EQ
            case 0x1 -> !regs.isZ();                             // NE
            case 0x2 -> regs.isC();                              // CS/HS
            case 0x3 -> !regs.isC();                             // CC/LO
            case 0x4 -> regs.isN();                              // MI
            case 0x5 -> !regs.isN();                             // PL
            case 0x6 -> regs.isV();                              // VS
            case 0x7 -> !regs.isV();                             // VC
            case 0x8 -> regs.isC() && !regs.isZ();              // HI
            case 0x9 -> !regs.isC() || regs.isZ();             // LS
            case 0xA -> regs.isN() == regs.isV();               // GE
            case 0xB -> regs.isN() != regs.isV();               // LT
            case 0xC -> !regs.isZ() && (regs.isN() == regs.isV()); // GT
            case 0xD -> regs.isZ() || (regs.isN() != regs.isV()); // LE
            default  -> true;                                     // AL
        };
    }

    // ── SVC / interrupt handling ──────────────────────────────────────────────

    private void handleSVC(int number) {
        LOG.fine(String.format("SVC #%d", number));
        // In a real emulator, this would invoke the RTOS / OS call table.
        // For now we simply log it.
    }

    private void checkInterrupts() {
        for (int i = 0; i < 32; i++) {
            if (irqEnabled[i] && irqPending[i]) {
                irqPending[i] = false;
                dispatchIRQ(i);
                break;
            }
        }
    }

    private void dispatchIRQ(int irq) {
        // Push exception frame (simplified – only PC and LR)
        regs.setSP(regs.getSP() - 32);
        memory.writeWord(regs.getSP() + 24, regs.getPC());
        memory.writeWord(regs.getSP() + 20, regs.getXPSR());
        regs.setLR(0xFFFFFFF9); // EXC_RETURN: return to Thread mode, main stack
        // Read handler from vector table (IRQ 0 is at offset 16)
        int handlerAddr = memory.readWord((irq + 16) * 4) & ~1;
        regs.setPC(handlerAddr);
    }

    public void triggerIRQ(int irq) {
        if (irq >= 0 && irq < 32) {
            irqPending[irq] = true;
        }
    }

    public void setIRQEnabled(int irq, boolean enabled) {
        if (irq >= 0 && irq < 32) irqEnabled[irq] = enabled;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Registers getRegisters() { return regs; }
    public long getInstructionCount() { return instructionCount; }

    public void addBreakpoint(int address) { breakpoints.add(address); }
    public void removeBreakpoint(int address) { breakpoints.remove(address); }
    public void clearBreakpoints() { breakpoints.clear(); }

    public void setStepListener(StepListener listener) { this.stepListener = listener; }
}
