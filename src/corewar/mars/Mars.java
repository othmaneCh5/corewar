package corewar.mars;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.instructions.Opecode;
import corewar.memory.Memory;
import corewar.process.Process;
import corewar.warrior.Warrior;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


public class Mars{
    private final Memory memory;
    private final int maxCycles;
    private int cycles = 0;


    private static class ProcEntry{
        final Warrior warrior;
        final Process process;
    

        ProcEntry(Warrior w, Process p){
            this.warrior = w;
            this.process = p;
        }
    }


    private final List<Warrior> warriors = new ArrayList<>();
    private final Deque<ProcEntry> queue = new ArrayDeque<>();

    public Mars(Memory memory, int maxCycles){
        this.memory = memory;
        this.maxCycles = maxCycles;
    }


    public void loadWarrior(int startPc,Instructions[] program){
        for(int i = 0;i< program.length; i++){
            memory.write(startPc + i, program[i]);
        }
    }

    public void addWarrior(Warrior w){
        warriors.add(w);
    
        if (!w.processes.isEmpty()) {
            queue.addLast(new ProcEntry(w, w.processes.getFirst()));
        }
    }

    private void execDat(Process p){
        p.kill();
    }

    private void execMOV(Process p, Instructions instruction) {
        int pc = memory.wrap(p.pc);

        int destAddr = memory.wrap(pc + instruction.parametreB);

        if (instruction.modeA == AddrMode.IMMEDIATE) {
            int x = instruction.parametreA;
            memory.write(destAddr, new Instructions(Opecode.DAT, x, 0));
            p.pc = memory.wrap(pc + 1);
            return;
        }

        int srcAddr = memory.wrap(pc + instruction.parametreA);
        Instructions src = memory.read(srcAddr);
        memory.write(destAddr, src);

        p.pc = memory.wrap(pc + 1);
    }

    private void execJMP(Process p, Instructions instr) {
        
        p.pc = memory.wrap(p.pc + instr.parametreA);
    }

    private void execADD(Process p, Instructions instr) {
        int pc = memory.wrap(p.pc);
        p.pc = pc;

        int v;
        if (instr.modeA == AddrMode.IMMEDIATE) {
            v = instr.parametreA;
        } else {
            int srcAddr = memory.wrap(pc + instr.parametreA);
            Instructions src = memory.read(srcAddr);
            v = src.parametreB; 
        }

        int destAddr = memory.wrap(pc + instr.parametreB);
        Instructions dest = memory.read(destAddr);

        Instructions newDest = new Instructions(
            dest.opecode,
            dest.modeA, dest.parametreA,
            dest.modeB, dest.parametreB + v
        );

        memory.write(destAddr, newDest);

        p.pc = memory.wrap(pc + 1);
    }



    private void stepOneCycle(){
        ProcEntry entry = queue.pollFirst();
        if(entry == null) {
            return;
        }
        
        Warrior w = entry.warrior;
        Process p = entry.process;

        if(!p.isAlive()){
            w.removeDeadProcesses();
            return;
        }

        int pc = memory.wrap(p.pc);
        p.pc = pc;
        Instructions instruction = memory.read(pc);

        switch (instruction.opecode) {
            case DAT: execDat(p); break;

            case MOV: execMOV(p, instruction); break;

            case JMP: execJMP(p, instruction); break; 

            case ADD: execADD(p, instruction); break;
        }

        w.removeDeadProcesses();

        if(p.isAlive()){
            queue.addLast(new ProcEntry(w, p)); 
        }
    }

    public Result run() {
        int cycles = 0;

        while (cycles < maxCycles) {
            if (queue.isEmpty()) break;

            int aliveCount = 0;
            Warrior lastAlive = null;
            for (Warrior w : warriors) {
                if (w.isAlive()) {
                    aliveCount++;
                    lastAlive = w;
                }
            }

            // Victoire si 1 seul warrior vivant
            if (aliveCount == 1) {
                return new Result(cycles, lastAlive, false); // gagnant
            }   
            if (aliveCount == 0) {
                return new Result(cycles, null, true); // plus personne: match nul
            }           

            stepOneCycle();
            cycles++;
        }
        Warrior winner = null;
        int aliveCount = 0;
        for(Warrior w : warriors){
            if (w.isAlive()){
            aliveCount++;
            winner = w;
            }
        }

        boolean draw = (aliveCount > 1 && cycles == maxCycles);
        return new Result(cycles, draw ? null : winner , draw);
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
        public final Warrior winner; // null si draw
        public final boolean draw;

        public Result(int cycles, Warrior winner, boolean draw) {
            this.cycles = cycles;
            this.winner = winner;
            this.draw = draw;
        }
    }
}

