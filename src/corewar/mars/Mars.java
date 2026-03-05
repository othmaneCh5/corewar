package corewar.mars;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.memory.Memory;
import corewar.process.Process;
import corewar.warrior.Warrior;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Mars {
    private final Memory memory;
    private final int maxCycles;
    private int cycles = 0;

    private static class ProcEntry {
        final Warrior warrior;
        final Process process;

        ProcEntry(Warrior w, Process p) {
            this.warrior = w;
            this.process = p;
        }
    }

    private final List<Warrior> warriors = new ArrayList<>();
    private final Deque<ProcEntry> queue = new ArrayDeque<>();

    public Mars(Memory memory, int maxCycles) {
        this.memory = memory;
        this.maxCycles = maxCycles;
    }

    public void loadWarrior(int startPc, Instructions[] program) {
        for (int i = 0; i < program.length; i++) {
            memory.write(startPc + i, program[i]);
        }
    }

    public void addWarrior(Warrior w) {
        warriors.add(w);
        for (Process p : w.getProcesses()) {
            queue.addLast(new ProcEntry(w, p));
        }
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Resolve the value of operand A of an instruction.
     * - IMMEDIATE : returns parametreA directly as a literal value
     * - DIRECT    : returns the B-field of the instruction at pc+parametreA
     */
    private int resolveA(int pc, Instructions instr) {
        if (instr.modeA == AddrMode.IMMEDIATE) {
            return instr.parametreA;
        }
        int srcAddr = memory.wrap(pc + instr.parametreA);
        return memory.read(srcAddr).parametreB;
    }

    /**
     * Resolve the destination address for operand B.
     * Always DIRECT in our subset: pc + parametreB (wrapped).
     */
    private int resolveDestAddr(int pc, Instructions instr) {
        return memory.wrap(pc + instr.parametreB);
    }

    // =========================================================
    // DAT — kills the process
    // =========================================================
    private void execDAT(Process p) {
        p.kill();
    }

    // =========================================================
    // MOV — copy
    //   MOV #v, B  => write v into dest.parametreB
    //   MOV  A, B  => copy whole instruction at A into B
    // =========================================================
    private void execMOV(Process p, Instructions instr) {
        int pc = memory.wrap(p.pc);

        if (instr.modeA == AddrMode.IMMEDIATE) {
            int destAddr = resolveDestAddr(pc, instr);
            Instructions dest = memory.read(destAddr);
            memory.write(destAddr, new Instructions(
                dest.opecode,
                dest.modeA, dest.parametreA,
                dest.modeB, instr.parametreA
            ));
        } else {
            int srcAddr  = memory.wrap(pc + instr.parametreA);
            int destAddr = resolveDestAddr(pc, instr);
            memory.write(destAddr, memory.read(srcAddr));
        }

        p.pc = memory.wrap(pc + 1);
    }

    // =========================================================
    // ADD — dest.B += v
    // =========================================================
    private void execADD(Process p, Instructions instr) {
        int pc       = memory.wrap(p.pc);
        int v        = resolveA(pc, instr);
        int destAddr = resolveDestAddr(pc, instr);
        Instructions dest = memory.read(destAddr);
        memory.write(destAddr, new Instructions(
            dest.opecode,
            dest.modeA, dest.parametreA,
            dest.modeB, dest.parametreB + v
        ));
        p.pc = memory.wrap(pc + 1);
    }

    // =========================================================
    // SUB — dest.B -= v  (mirror of ADD)
    // =========================================================
    private void execSUB(Process p, Instructions instr) {
        int pc       = memory.wrap(p.pc);
        int v        = resolveA(pc, instr);
        int destAddr = resolveDestAddr(pc, instr);
        Instructions dest = memory.read(destAddr);
        memory.write(destAddr, new Instructions(
            dest.opecode,
            dest.modeA, dest.parametreA,
            dest.modeB, dest.parametreB - v
        ));
        p.pc = memory.wrap(pc + 1);
    }

    // =========================================================
    // JMP — unconditional jump
    // =========================================================
    private void execJMP(Process p, Instructions instr) {
        p.pc = memory.wrap(p.pc + instr.parametreA);
    }

    // =========================================================
    // JMZ — Jump if Zero
    //   If the resolved value of B == 0, jump to pc+A
    //   Otherwise advance pc normally
    // =========================================================
    private void execJMZ(Process p, Instructions instr) {
        int pc = memory.wrap(p.pc);
        int destAddr = resolveDestAddr(pc, instr);
        Instructions dest = memory.read(destAddr);
        if (dest.parametreB == 0) {
            p.pc = memory.wrap(pc + instr.parametreA);
        } else {
            p.pc = memory.wrap(pc + 1);
        }
    }

    // =========================================================
    // JMN — Jump if Not Zero
    //   If the resolved value of B != 0, jump to pc+A
    //   Otherwise advance pc normally
    // =========================================================
    private void execJMN(Process p, Instructions instr) {
        int pc = memory.wrap(p.pc);
        int destAddr = resolveDestAddr(pc, instr);
        Instructions dest = memory.read(destAddr);
        if (dest.parametreB != 0) {
            p.pc = memory.wrap(pc + instr.parametreA);
        } else {
            p.pc = memory.wrap(pc + 1);
        }
    }

    // =========================================================
    // DJN — Decrement and Jump if Not Zero
    //   Decrement dest.B by 1, then if dest.B != 0 jump to pc+A
    //   Used to implement counted loops without ADD.
    // =========================================================
    private void execDJN(Process p, Instructions instr) {
        int pc       = memory.wrap(p.pc);
        int destAddr = resolveDestAddr(pc, instr);
        Instructions dest = memory.read(destAddr);
        int newB = dest.parametreB - 1;
        memory.write(destAddr, new Instructions(
            dest.opecode,
            dest.modeA, dest.parametreA,
            dest.modeB, newB
        ));
        if (newB != 0) {
            p.pc = memory.wrap(pc + instr.parametreA);
        } else {
            p.pc = memory.wrap(pc + 1);
        }
    }

    // =========================================================
    // CMP (aka SEQ) — Skip if Equal
    //   Compare the B-fields of the instructions at pc+A and pc+B.
    //   If equal, skip the next instruction (pc += 2).
    //   Otherwise advance normally (pc += 1).
    //   Used in scanners to detect untouched (DAT) memory.
    // =========================================================
    private void execCMP(Process p, Instructions instr) {
        int pc      = memory.wrap(p.pc);
        int addrA   = memory.wrap(pc + instr.parametreA);
        int addrB   = resolveDestAddr(pc, instr);
        Instructions iA = memory.read(addrA);
        Instructions iB = memory.read(addrB);
        // Full instruction comparison (opcode + both fields)
        boolean equal = iA.opecode    == iB.opecode
                     && iA.parametreA == iB.parametreA
                     && iA.parametreB == iB.parametreB;
        p.pc = memory.wrap(pc + (equal ? 2 : 1));
    }

    // =========================================================
    // SNE — Skip if Not Equal  (opposite of CMP)
    //   If the two instructions are NOT equal, skip next (pc += 2).
    //   Otherwise advance normally (pc += 1).
    // =========================================================
    private void execSNE(Process p, Instructions instr) {
        int pc      = memory.wrap(p.pc);
        int addrA   = memory.wrap(pc + instr.parametreA);
        int addrB   = resolveDestAddr(pc, instr);
        Instructions iA = memory.read(addrA);
        Instructions iB = memory.read(addrB);
        boolean equal = iA.opecode    == iB.opecode
                     && iA.parametreA == iB.parametreA
                     && iA.parametreB == iB.parametreB;
        p.pc = memory.wrap(pc + (equal ? 1 : 2));
    }

    // =========================================================
    // SPL — Split process
    //   Spawns a new process at pc+A, current process continues at pc+1
    // =========================================================
    private void execSPL(Process p, Instructions instr, Warrior w) {
        int newPc = memory.wrap(p.pc + instr.parametreA);
        Process newProcess = new Process(newPc);
        w.addProcess(newProcess);
        queue.addLast(new ProcEntry(w, newProcess));
        p.pc = memory.wrap(p.pc + 1);
    }

    // =========================================================
    // Core execution loop
    // =========================================================

    private void stepOneCycle() {
        ProcEntry entry = queue.pollFirst();
        if (entry == null) return;

        Warrior w = entry.warrior;
        Process p = entry.process;

        if (!p.isAlive()) {
            w.removeDeadProcesses();
            return;
        }

        int pc = memory.wrap(p.pc);
        p.pc = pc;
        Instructions instruction = memory.read(pc);

        switch (instruction.opecode) {
            case DAT: execDAT(p);                   break;
            case MOV: execMOV(p, instruction);      break;
            case ADD: execADD(p, instruction);      break;
            case SUB: execSUB(p, instruction);      break;
            case JMP: execJMP(p, instruction);      break;
            case JMZ: execJMZ(p, instruction);      break;
            case JMN: execJMN(p, instruction);      break;
            case DJN: execDJN(p, instruction);      break;
            case CMP: execCMP(p, instruction);      break;
            case SNE: execSNE(p, instruction);      break;
            case SPL: execSPL(p, instruction, w);   break;
        }

        if (p.isAlive()) {
            queue.addLast(new ProcEntry(w, p));
        } else {
            w.removeDeadProcesses();
        }
    }

    private int countAlive(Warrior[] result) {
        int count = 0;
        for (Warrior w : warriors) {
            if (w.isAlive()) {
                count++;
                result[0] = w;
            }
        }
        return count;
    }

    public Result run() {
        int cycles = 0;

        while (cycles < maxCycles) {
            if (queue.isEmpty()) break;

            stepOneCycle();
            cycles++;

            Warrior[] last = new Warrior[1];
            int aliveCount = countAlive(last);

            if (aliveCount == 1) return new Result(cycles, last[0], false);
            if (aliveCount == 0) return new Result(cycles, null, true);
        }

        Warrior[] last = new Warrior[1];
        int aliveCount = countAlive(last);
        boolean draw = (aliveCount != 1);
        return new Result(cycles, draw ? null : last[0], draw);
    }

    public void step() {
        stepOneCycle();
        cycles++;
    }

    public int getCycles() {
        return cycles;
    }

    public static class Result {
        public final int cycles;
        public final Warrior winner;
        public final boolean draw;

        public Result(int cycles, Warrior winner, boolean draw) {
            this.cycles = cycles;
            this.winner = winner;
            this.draw = draw;
        }
    }
}