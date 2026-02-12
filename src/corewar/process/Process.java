package corewar.process;

public class Process{

    public int pc;
    public boolean alive;

    public Process(int pc){
        this.pc = pc;
        this.alive = true;
    }

    public int getPc() { 
        return pc; 
    }

    public boolean isAlive() {
        return alive; 
    }

    
    public void kill(){
        alive = false;
    }
}