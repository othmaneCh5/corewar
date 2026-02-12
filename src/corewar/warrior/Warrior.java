package corewar.warrior;

import corewar.process.Process;
import java.util.LinkedList;

public class Warrior{

    public LinkedList<Process> processes = new LinkedList<>();
    public String name;
    public int startPc;


    public Warrior (int startPc, String name){
        processes.add(new Process(startPc));
        this.startPc = startPc;
        this.name = name;
    }


    public String getName(){
        return name;
    }

    public void removeDeadProcesses(){
        processes.removeIf((process) -> !process.isAlive());
    }

    public boolean isAlive(){
        return !processes.isEmpty();
    }
}