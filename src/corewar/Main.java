package corewar;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.instructions.Opecode;
import corewar.mars.Mars;
import corewar.memory.Memory;
import corewar.warrior.Warrior;

public class Main {

    private static int passed = 0;
    private static int failed = 0;

    private static void ok(String name) {
        System.out.println("✅ OK  - " + name);
        passed++;
    }

    private static void ko(String name, String details) {
        System.out.println("❌ FAIL- " + name + " | " + details);
        failed++;
    }

    private static void assertTrue(String name, boolean cond, String detailsIfFail) {
        if (cond) ok(name);
        else ko(name, detailsIfFail);
    }

    private static void assertInstrEquals(String name, Instructions got, Instructions exp) {
        boolean same =
                got.opecode    == exp.opecode    &&
                got.modeA      == exp.modeA      &&
                got.parametreA == exp.parametreA &&
                got.modeB      == exp.modeB      &&
                got.parametreB == exp.parametreB;
        assertTrue(name, same, "got=" + got + " exp=" + exp);
    }

    private static void assertEquals(String name, int got, int exp) {
        assertTrue(name, got == exp, "got=" + got + " exp=" + exp);
    }

    private static Memory mem;
    private static Mars   mars;

    private static void reset() {
        mem  = new Memory();
        mars = new Mars(mem, 10000);
    }

    // =========================================================
    // DAT
    // =========================================================

    private static void testDATKills() {
        reset();
        Warrior w = new Warrior(0, "DAT");
        mars.loadWarrior(0, new Instructions[]{new Instructions(Opecode.DAT, 0, 0)});
        mars.addWarrior(w);
        mars.step();
        assertTrue("DAT kills => warrior dead", !w.isAlive(), "warrior still alive");
    }

    // =========================================================
    // MOV
    // =========================================================

    private static void testMOVDirectCopies() {
        reset();
        Warrior w = new Warrior(0, "MOV-direct");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, 1, 2),
                new Instructions(Opecode.DAT, 7, 8),
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);
        mars.step();
        assertInstrEquals("MOV direct copies whole instruction",
                mem.read(2), new Instructions(Opecode.DAT, 7, 8));
    }

    private static void testMOVImmediateWritesToDestB() {
        reset();
        Warrior w = new Warrior(0, "MOV-imm");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 5, AddrMode.DIRECT, 1),
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);
        mars.step();
        assertInstrEquals("MOV #5,1 writes value into dest.B => DAT 0,5",
                mem.read(1),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 5));
    }

    private static void testMOVDirectToSelf() {
        reset();
        Warrior w = new Warrior(0, "MOV-self");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, 0, 0),
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);
        mars.step();
        assertInstrEquals("MOV 0,0 overwrites self (stays MOV 0,0)",
                mem.read(0), new Instructions(Opecode.MOV, 0, 0));
        assertTrue("MOV self => warrior still alive", w.isAlive(), "warrior died");
    }

    // =========================================================
    // ADD
    // =========================================================

    private static void testADDImmediateAddsToDestB() {
        reset();
        Warrior w = new Warrior(0, "ADD-imm");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 3, AddrMode.DIRECT, 1),
                new Instructions(Opecode.DAT, 0, 4)
        });
        mars.addWarrior(w);
        mars.step();
        assertInstrEquals("ADD #3,1 makes dest.B = 7",
                mem.read(1),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 7));
    }

    private static void testADDDirectUsesSourceB() {
        reset();
        Warrior w = new Warrior(0, "ADD-direct");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.ADD, 1, 2),
                new Instructions(Opecode.DAT, 0, 9),
                new Instructions(Opecode.DAT, 0, 1)
        });
        mars.addWarrior(w);
        mars.step();
        assertInstrEquals("ADD 1,2 => dest.B = 10",
                mem.read(2),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 10));
    }

    private static void testADDWrapsDestB() {
        reset();
        Warrior w = new Warrior(0, "ADD-wrap");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 3, AddrMode.DIRECT, 1),
                new Instructions(Opecode.DAT, 0, Memory.SIZE - 1)
        });
        mars.addWarrior(w);
        mars.step();
        assertEquals("ADD wrap: raw stored value = 8002",
                mem.read(1).parametreB, Memory.SIZE - 1 + 3);
    }

    // =========================================================
    // JMP
    // =========================================================

    private static void testJMPJumps() {
        reset();
        Warrior w = new Warrior(0, "JMP");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.JMP, 2, 0),
                new Instructions(Opecode.DAT, 0, 0),
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 9, AddrMode.DIRECT, 10),
        });
        mars.addWarrior(w);
        mars.step();
        assertTrue("After JMP, warrior still alive", w.isAlive(), "warrior died");
        mars.step();
        assertInstrEquals("JMP landed on addr 2 (marker at 12)",
                mem.read(12),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 9));
    }

    // =========================================================
    // SPL
    // =========================================================

    private static void testSPLCreatesProcess() {
        reset();
        Warrior w = new Warrior(0, "SPL");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.SPL, 2, 0),
                new Instructions(Opecode.DAT, 0, 0),
                new Instructions(Opecode.DAT, 0, 0),
        });
        mars.addWarrior(w);
        mars.step();
        assertTrue("After SPL: alive (2 processes)", w.isAlive(), "died after SPL");
        mars.step();
        assertTrue("After step2: alive (1 process left)", w.isAlive(), "died too early");
        mars.step();
        assertTrue("After step3: dead (both killed)", !w.isAlive(), "still alive");
    }

    // =========================================================
    // PC increment
    // =========================================================

    private static void testPCIncrements() {
        reset();
        Warrior w = new Warrior(0, "PC+1");
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 7, AddrMode.DIRECT, 5),
                new Instructions(Opecode.DAT, 0, 0)
        });
        mars.addWarrior(w);
        mars.step();
        assertInstrEquals("Marker written at addr 5",
                mem.read(5),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 7));
        mars.step();
        assertTrue("Warrior dead => PC incremented correctly", !w.isAlive(), "still alive");
    }

    // =========================================================
    // Mémoire circulaire
    // =========================================================

    private static void testMemoryWrap() {
        reset();
        int lastAddr = Memory.SIZE - 1;
        Warrior w = new Warrior(lastAddr, "WRAP");
        mars.loadWarrior(lastAddr, new Instructions[]{
                new Instructions(Opecode.JMP, 1, 0),
        });
        mars.loadWarrior(0, new Instructions[]{
                new Instructions(Opecode.MOV, AddrMode.IMMEDIATE, 42, AddrMode.DIRECT, 5),
        });
        mars.addWarrior(w);
        mars.step();
        assertTrue("After JMP at 7999, warrior alive", w.isAlive(), "warrior died");
        mars.step();
        assertInstrEquals("Wrap: JMP 7999+1 => addr 0, marker at 5",
                mem.read(5),
                new Instructions(Opecode.DAT, AddrMode.DIRECT, 0, AddrMode.DIRECT, 42));
    }

    // =========================================================
    // 2 Warriors
    // =========================================================

    private static void testTwoWarriorsOneWins() {
        reset();
        Warrior wA = new Warrior(0,    "Bomber");
        Warrior wB = new Warrior(4003, "Sitting Duck"); // 4003 is in the bomber's hit sequence

        // Bomber auto-modifiant : bombe toutes les 100 cases à partir de 603.
        // addr 0 : ADD #100 sur le champ B de addr 1 => la cible avance de 100 à chaque boucle
        // addr 1 : MOV addr+2 (le DAT bombe) vers la cible (B part de 502, +100 par tour ADD-first)
        //          => 1er tir : wrap(1 + 602) = 603, 2e : 703, ... 35e : wrap(1+3502)=3503... 
        //          séquence exacte : 603,703,...,3903,4003 => duck à 4003 touché au loop 35 (~105 cycles)
        // addr 2 : JMP -2 => retour addr 0, le bomber ne tombe jamais sur le DAT
        // addr 3 : DAT bombe (source du MOV uniquement)
        mars.loadWarrior(0, new Instructions[]{
            new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 100, AddrMode.DIRECT, 1),
            new Instructions(Opecode.MOV, AddrMode.DIRECT, 2, AddrMode.DIRECT, 502),
            new Instructions(Opecode.JMP, -2, 0),
            new Instructions(Opecode.DAT, 0, 0),
        });

        mars.loadWarrior(4003, new Instructions[]{
            new Instructions(Opecode.JMP, 0, 0),
        });

        mars.addWarrior(wA);
        mars.addWarrior(wB);

        Mars.Result result = mars.run();
        assertTrue("Bomber vs Duck: no draw",   !result.draw,              "unexpected draw");
        assertTrue("Bomber vs Duck: has winner", result.winner != null,    "no winner");
        assertTrue("Bomber wins",
                result.winner != null && result.winner.getName().equals("Bomber"),
                "wrong winner: " + (result.winner == null ? "null" : result.winner.getName()));
    }

    private static void testTwoWarriorsDraw() {
        // Un vrai draw se produit quand maxCycles est atteint avec plusieurs warriors encore vivants.
        // Il est impossible de faire mourir deux warriors au même cycle en exécution séquentielle :
        // le moteur traite un processus à la fois, donc l'un mourra toujours avant l'autre.
        // => On teste le draw via maxCycles=1 avec deux warriors qui bouclent indéfiniment.
        mem  = new Memory();
        mars = new Mars(mem, 1);

        Warrior wA = new Warrior(0,   "A");
        Warrior wB = new Warrior(100, "B");

        mars.loadWarrior(0,   new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});
        mars.loadWarrior(100, new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});

        mars.addWarrior(wA);
        mars.addWarrior(wB);

        Mars.Result result = mars.run();
        assertTrue("maxCycles with 2 alive => draw", result.draw,           "expected draw");
        assertTrue("Draw => no winner",              result.winner == null, "unexpected winner");
    }

    private static void testMaxCyclesReachedIsDraw() {
        mem = new Memory();
        Mars shortMars = new Mars(mem, 10);
        Warrior wA = new Warrior(0,   "A");
        Warrior wB = new Warrior(100, "B");
        shortMars.loadWarrior(0,   new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});
        shortMars.loadWarrior(100, new Instructions[]{new Instructions(Opecode.JMP, 0, 0)});
        shortMars.addWarrior(wA);
        shortMars.addWarrior(wB);

        Mars.Result result = shortMars.run();
        assertTrue("maxCycles => draw",        result.draw, "expected draw");
        assertEquals("maxCycles => 10 cycles", result.cycles, 10);
    }

    // =========================================================
    // Main
    // =========================================================

    public static void main(String[] args) {
        System.out.println("=== Corewar Tests ===\n");

        System.out.println("-- DAT --");
        testDATKills();

        System.out.println("\n-- MOV --");
        testMOVDirectCopies();
        testMOVImmediateWritesToDestB();
        testMOVDirectToSelf();

        System.out.println("\n-- ADD --");
        testADDImmediateAddsToDestB();
        testADDDirectUsesSourceB();
        testADDWrapsDestB();

        System.out.println("\n-- JMP --");
        testJMPJumps();

        System.out.println("\n-- SPL --");
        testSPLCreatesProcess();

        System.out.println("\n-- PC --");
        testPCIncrements();

        System.out.println("\n-- Memory wrap --");
        testMemoryWrap();

        System.out.println("\n-- 2 Warriors --");
        testTwoWarriorsOneWins();
        testTwoWarriorsDraw();
        testMaxCyclesReachedIsDraw();

        System.out.println("\n=== Summary ===");
        System.out.println("Passed : " + passed);
        System.out.println("Failed : " + failed);
        if (failed == 0) System.out.println("✅ All tests passed!");
        else             System.out.println("❌ Some tests failed.");
    }
}
