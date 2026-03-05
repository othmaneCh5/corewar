package corewar.instructions;

public class Instructions {

    public final Opecode opecode;
    public final AddrMode modeA;
    public final int parametreA;
    public final AddrMode modeB;
    public final int parametreB;

    public Instructions(Opecode opecode, AddrMode modeA, int parametreA,AddrMode modeB, int parametreB) {
        this.opecode = opecode;
        this.modeA = modeA;
        this.parametreA = parametreA;
        this.modeB = modeB;
        this.parametreB = parametreB;
    }

    public Instructions(Opecode opcode, int parametreA, int parametreB) {
        this(opcode, AddrMode.DIRECT, parametreA, AddrMode.DIRECT, parametreB);
    }

    public static Instructions dat() {
        return new Instructions(Opecode.DAT, 0, 0);
    }

    public Instructions copy() {
        return new Instructions(this.opecode, this.modeA, this.parametreA,
                                this.modeB, this.parametreB);
    }

    @Override
    public String toString() {
        String aPrefix = (modeA == AddrMode.IMMEDIATE) ? "#" : "";
        String bPrefix = (modeB == AddrMode.IMMEDIATE) ? "#" : "";
        return opecode + " " + aPrefix + parametreA + ", " + bPrefix + parametreB;
    }
}

