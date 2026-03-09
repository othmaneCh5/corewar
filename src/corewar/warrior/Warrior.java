package corewar.warrior;

import corewar.process.Process;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Warrior {

    private final LinkedList<Process> processes = new LinkedList<>();
    private final String name;
    private final int startPc;

    public Warrior(int startPc, String name) {
        this.startPc = startPc;
        this.name = name;
        processes.add(new Process(startPc));
    }

    public String getName() {
        return name;
    }

    public int getStartPc() {
        return startPc;
    }

    public boolean hasProcesses() {
        return isAlive();
    }

    public Process getFirstProcess() {
        return processes.getFirst();
    }

    
    public List<Process> getProcesses() {
        return Collections.unmodifiableList(processes);
    }

    public void addProcess(Process p) {
        processes.add(p);
    }

    public void removeDeadProcesses() {
        processes.removeIf(p -> !p.isAlive());
    }

    public boolean isAlive() {
        return !processes.isEmpty();
    }
}