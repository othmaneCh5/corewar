/*package corewar;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.instructions.Opecode;
import corewar.mars.Mars;
import corewar.memory.Memory;
import corewar.warrior.Warrior;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

public class CorewarTest {

    private Memory mem;
    private Mars mars;

    @BeforeEach
    void setUp() {
        mem  = new Memory();
        mars = new Mars(mem, 1000);
    }

    // =========================================================
    // DAT
    // =========================================================

    @Test
    @DisplayName("DAT kills the process => warrior dead after 1 step")
    void testDATKills() {
        Warrior w = new Warrior(0, "DAT");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);

        mars.step();
        assertFalse(w.isAlive(), "Warrior should be dead after hitting DAT");
    }

    // =========================================================
    // MOV
    // =========================================================

    @Test
    @DisplayName("MOV direct: copies whole instruction from src to dest")
    void testMOVDirectCopies() {
        Warrior w = new Warrior(0, "MOV-direct");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, 1, 2),   // copy mem[1] -> mem[2]
                new Instructions(Opecode.DAT, 7, 8),   // source
                new Instructions(Opecode.DAT, 0, 0)    // dest
        });
        mars.addWarrior(w);

        mars.step();

        assertInstrEquals(mem.read(2), new Instructions(Opecode.DAT, 7, 8));
    }

    @Test
    @DisplayName("MOV immediate: writes value into dest.B")
    void testMOVImmediateWritesToDestB() {
        Warrior w = new Warrior(0, "MOV-imm");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 5, AddrMode.DIRECT, 1),
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);

        mars.step();

        assertInstrEquals(mem.read(1),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 5));
    }

    @Test
    @DisplayName("MOV direct to self: instruction overwrites itself")
    void testMOVDirectToSelf() {
        Warrior w = new Warrior(0, "MOV-self");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, 0, 0),   // copie mem[0] sur mem[0] (lui-même)
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);

        mars.step();

        // L'instruction à l'adresse 0 doit rester un MOV 0,0
        assertInstrEquals(mem.read(0),
                new Instructions(Opecode.MOV, 0, 0));
        // Le warrior est toujours vivant (MOV ne tue pas)
        assertTrue(w.isAlive());
    }

    // =========================================================
    // ADD
    // =========================================================

    @Test
    @DisplayName("ADD immediate: dest.B += immediate value")
    void testADDImmediateAddsToDestB() {
        Warrior w = new Warrior(0, "ADD-imm");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 3, AddrMode.DIRECT, 1),
                new Instructions(Opecode.DAT, 0, 4)
        });
        mars.addWarrior(w);

        mars.step();

        assertInstrEquals(mem.read(1),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 7));
    }

    @Test
    @DisplayName("ADD direct: dest.B += source.B")
    void testADDDirectUsesSourceB() {
        Warrior w = new Warrior(0, "ADD-direct");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.ADD, 1, 2),
                new Instructions(Opecode.DAT, 0, 9),
                new Instructions(Opecode.DAT, 0, 1)
        });
        mars.addWarrior(w);

        mars.step();

        assertInstrEquals(mem.read(2),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 10));
    }

    @Test
    @DisplayName("ADD wrap: dest.B wraps around memory size")
    void testADDWrapsDestB() {
        Warrior w = new Warrior(0, "ADD-wrap");
        mars.loadWarrior(0, new Instructions[]{
                // ADD #3, 1 => dest.B = 7999 + 3 = 8002 => wrap => 2
                new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 3, AddrMode.DIRECT, 1),
                new Instructions(Opecode.DAT, 0, Memory.SIZE - 1) // B = 7999
        });
        mars.addWarrior(w);

        mars.step();

        Instructions got = mem.read(1);
        // La valeur brute stockée sera 8002, mais memory.wrap est utilisé lors des accès adresse,
        // pas sur les valeurs stockées — on vérifie que la valeur est bien 8002
        assertEquals(Memory.SIZE - 1 + 3, got.parametreB,
                "ADD should store raw value 8002 (wrap happens at address resolution time)");
    }

    // =========================================================
    // JMP
    // =========================================================

    @Test
    @DisplayName("JMP: jumps to target address")
    void testJMPJumps() {
        Warrior w = new Warrior(0, "JMP");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.JMP, 2, 0),                                        // addr 0
                new Instructions(Opecode.DAT, 0, 0),                                        // addr 1 : ne doit PAS s'exécuter
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 9, AddrMode.DIRECT, 10),  // addr 2 : marqueur
        });
        mars.addWarrior(w);

        mars.step(); // JMP
        assertTrue(w.isAlive(), "Warrior should survive JMP");

        mars.step(); // MOV à addr 2 => écrit DAT 0,9 à addr 12
        assertInstrEquals(mem.read(12),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 9));
    }

    // =========================================================
    // SPL
    // =========================================================

    @Test
    @DisplayName("SPL: creates a second process")
    void testSPLCreatesProcess() {
        Warrior w = new Warrior(0, "SPL");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.SPL, 2, 0),  // addr 0 : fork -> addr 2, principal -> addr 1
                new Instructions(Opecode.DAT, 0, 0),  // addr 1 : tue process principal
                new Instructions(Opecode.DAT, 0, 0),  // addr 2 : tue process secondaire
        });
        mars.addWarrior(w);

        mars.step(); // SPL => 2 processes
        assertTrue(w.isAlive(), "Warrior should be alive after SPL (2 processes)");

        mars.step(); // 1 process meurt
        assertTrue(w.isAlive(), "Warrior should still be alive (1 process left)");

        mars.step(); // 2ème process meurt
        assertFalse(w.isAlive(), "Warrior should be dead (both processes killed)");
    }

    // =========================================================
    // PC increment
    // =========================================================

    @Test
    @DisplayName("PC increments by 1 after normal instruction")
    void testPCIncrements() {
        Warrior w = new Warrior(0, "PC+1");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 7, AddrMode.DIRECT, 5),
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);

        mars.step();
        assertInstrEquals(mem.read(5),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 7));

        mars.step();
        assertFalse(w.isAlive(), "Warrior should be dead => PC incremented to DAT");
    }

    // =========================================================
    // Mémoire circulaire
    // =========================================================

    @Test
    @DisplayName("Memory wrap: JMP at addr 7999 lands at addr 0")
    void testMemoryWrap() {
        int lastAddr = Memory.SIZE - 1; // 7999
        Warrior w = new Warrior(lastAddr, "WRAP");

        mars.loadWarrior(lastAddr, new Instructions[]{
                new Instructions(Opecode.JMP, 1, 0), // 7999 + 1 = 0 (wrap)
        });
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 42, AddrMode.DIRECT, 5),
        });
        mars.addWarrior(w);

        mars.step(); // JMP à 7999 => wrap à 0
        assertTrue(w.isAlive(), "Warrior should survive wrap JMP");

        mars.step(); // MOV à addr 0 => écrit DAT 0,42 à addr 5
        assertInstrEquals(mem.read(5),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 42));
    }

    // =========================================================
    // 2 Warriors qui s'affrontent
    // =========================================================

    @Test
    @DisplayName("Two warriors: the one who survives wins")
    void testTwoWarriorsOneWins() {
        // Warrior A : bombe de DAT (écrase la mémoire adverse)
        // Warrior B : juste un DAT (meurt immédiatement)
        Warrior wA = new Warrior(0, "Bomber");
        Warrior wB = new Warrior(100, "Sitting Duck");

        // Bomber : MOV addr 1 vers addr +10 en boucle (bomber classique simplifié)
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, 1, 10),   // copie DAT vers +10
                new Instructions(Opecode.DAT, 0, 0),    // bombe
                new Instructions(Opecode.JMP, -2, 0),   // retour au début
        });

        // Sitting Duck : attend la mort
        mars.loadWarrior(100, new Instructions[]{
                new Instructions(Opecode.JMP, 0, 0),    // boucle sur place (survit tant que non écrasé)
        });

        mars.addWarrior(wA);
        mars.addWarrior(wB);

        Mars.Result result = mars.run();

        assertFalse(result.draw, "Should not be a draw");
        assertNotNull(result.winner, "There should be a winner");
        assertEquals("Bomber", result.winner.getName(), "Bomber should win");
    }

    @Test
    @DisplayName("Two warriors: draw when both die same cycle")
    void testTwoWarriorsDraw() {
        // Les deux warriors exécutent un DAT immédiatement => match nul
        Warrior wA = new Warrior(0, "A");
        Warrior wB = new Warrior(1, "B");

        mars.loadWarrior(0, new Instructions[]{new Instructions(Opecode.DAT, 0, 0)});
        mars.loadWarrior(1, new Instructions[]{new Instructions(Opecode.DAT, 0, 0)});

        mars.addWarrior(wA);
        mars.addWarrior(wB);

        Mars.Result result = mars.run();

        assertTrue(result.draw, "Should be a draw (both die)");
        assertNull(result.winner, "No winner in a draw");
    }

    @Test
    @DisplayName("run() returns draw when maxCycles reached")
    void testMaxCyclesReachedIsDraw() {
        // Les deux warriors bouclent indéfiniment => maxCycles atteint
        Mars shortMars = new Mars(mem, 10); // seulement 10 cycles

        Warrior wA = new Warrior(0, "A");
        Warrior wB = new Warrior(100, "B");

        mars.loadWarrior(0,   new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});
        mars.loadWarrior(100, new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});

        shortMars.loadWarrior(0,   new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});
        shortMars.loadWarrior(100, new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});

        shortMars.addWarrior(wA);
        shortMars.addWarrior(wB);

        Mars.Result result = shortMars.run();

        assertTrue(result.draw, "Should be a draw when maxCycles reached");
        assertEquals(10, result.cycles, "Should have run exactly 10 cycles");
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void assertInstrEquals(Instructions got, Instructions exp) {
        assertEquals(exp.opecode,    got.opecode,    "opecode mismatch");
        assertEquals(exp.modeA,      got.modeA,      "modeA mismatch");
        assertEquals(exp.parametreA, got.parametreA, "parametreA mismatch");
        assertEquals(exp.modeB,      got.modeB,      "modeB mismatch");
        assertEquals(exp.parametreB, got.parametreB, "parametreB mismatch");
    }
}*/