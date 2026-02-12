package corewar;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.instructions.Opecode;
import corewar.mars.Mars;
import corewar.memory.Memory;
import corewar.warrior.Warrior;

public class Main {

    // Réglages console
    private static final int maxCycles = 1000;   // sécurité
    private static final int PRINT_EVERY = 1;      // 1 = affiche chaque cycle (mets 10 ou 50 si trop verbeux)
    private static final int SLEEP_MS = 80;        // ralentit (0 si tu veux rapide)

    public static void main(String[] args) throws InterruptedException {

        Memory memory = new Memory();
        Mars mars = new Mars(memory, 200000); // maxCycles côté MARS (grand)

        // ===== Starts =====
        int start1 = 0;
        int start2 = 4000;
        int start3 = 500;

        // ===== Warriors =====
        Warrior warrior1 = new Warrior(start1, "Imp");
        Warrior warrior2 = new Warrior(start2, "SlowBomber");
        Warrior warrior3 = new Warrior(start3, "Imp2");

        // ===== Programmes =====

        // Imp classique
        Instructions[] imp = {
            new Instructions(Opecode.MOV, 0, 20),
            new Instructions(Opecode.JMP, 7, 0)
        };

        // Bomber lent (nécessite ADD implémenté dans Mars)
        Instructions[] slowBomber = {
            new Instructions(Opecode.MOV, AddrMode.DIRECT, 3, AddrMode.DIRECT, 200),
            new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 1, AddrMode.DIRECT, 1),
            new Instructions(Opecode.JMP, AddrMode.DIRECT, 8, AddrMode.DIRECT, 0),
            new Instructions(Opecode.DAT, 0, 0)
        };

        // Imp2 (tel que dans ton GUI)
        Instructions[] imp2 = {
            new Instructions(Opecode.MOV, 0, 5),
            new Instructions(Opecode.JMP, 4, 0)
        };

        // ===== Load + add =====
        mars.loadWarrior(start1, imp);
        mars.addWarrior(warrior1);

        mars.loadWarrior(start2, slowBomber);
        mars.addWarrior(warrior2);

        mars.loadWarrior(start3, imp2);
        mars.addWarrior(warrior3);

        // ===== Loop cycle par cycle =====
        while (mars.getCycles() < maxCycles
                && warrior1.isAlive()
                && warrior2.isAlive()
                && warrior3.isAlive()) {

            mars.step(); // 1 instruction exécutée (1 cycle)

            if (mars.getCycles() % PRINT_EVERY == 0) {
                System.out.printf(
                        "Cycle=%d | Imp=%d proc | SlowBomber=%d proc | Imp2=%d proc%n",
                        mars.getCycles(),
                        warrior1.processes.size(),
                        warrior2.processes.size(),
                        warrior3.processes.size()
                );
            }

            

            if (SLEEP_MS > 0) Thread.sleep(SLEEP_MS);
        }

        // ===== Résultat =====
        System.out.println("=== GAME OVER ===");
        System.out.println("Cycles: " + mars.getCycles());

        // Déterminer gagnant (si un seul vivant)
        int aliveCount = 0;
        Warrior winner = null;

        if (warrior1.isAlive()) { aliveCount++; winner = warrior1; }
        if (warrior2.isAlive()) { aliveCount++; winner = warrior2; }
        if (warrior3.isAlive()) { aliveCount++; winner = warrior3; }

        if (mars.getCycles() >= maxCycles || aliveCount != 1) {
            System.out.println("Result: DRAW");
        } else {
            System.out.println("Winner: " + winner.getName());
        }
    }
}


        /*Memory memory = new Memory();
        Mars mars = new Mars(memory, 20000); // maxCycles

        // ===== Warrior 1 : IMP =====
        int start1 = 0;
        Warrior w1 = new Warrior(start1, "Imp1");

        Instructions[] imp1 = new Instructions[] {
            new Instructions(Opecode.MOV, 0, 1),
            new Instructions(Opecode.JMP, -1, 0)
        };

        mars.loadWarrior(start1, imp1);
        mars.addWarrior(w1);

        // ===== Warrior 2 : IMP (autre adresse) =====
        int start2 = 10;
        Warrior w2 = new Warrior(start2, "Imp2");

        Instructions[] imp2 = new Instructions[] {
            new Instructions(Opecode.MOV, 0, 1),
            new Instructions(Opecode.JMP, -1, 0)
        };

        mars.loadWarrior(start2, imp2);
        mars.addWarrior(w2);

        // ===== Warrior 3 : mini "dwarf" simplifié (pas le vrai) =====
        int start3 = 100;
        Warrior w3 = new Warrior(start3, "Dwarf");

        Instructions[] dwarf = new Instructions[] {
            new Instructions(Opecode.MOV, 2, 1),
            new Instructions(Opecode.JMP, -1, 0),
            new Instructions(Opecode.DAT, 0, 0)
        };

        mars.loadWarrior(start3, dwarf);
        mars.addWarrior(w3);

        // ===== Run =====
        Mars.Result r = mars.run();
        if (r.draw) {
            System.out.println("MATCH NUL après " + r.cycles + " cycles");
        } else {
            System.out.println("GAGNANT = " + r.winner.getName() + " après " + r.cycles + " cycles");
        }

        int start = 50;

    Instructions[] prog = {
    new Instructions(Opecode.MOV, 0, 1),
    new Instructions(Opecode.JMP, -1, 0),
    new Instructions(Opecode.DAT, 0, 0)
    };

    mars.loadWarrior(start, prog);

// Vérification (affiche les 3 cases chargées)
    System.out.println("Case " + start     + " = " + memory.read(start));
    System.out.println("Case " + (start+1) + " = " + memory.read(start+1));
    System.out.println("Case " + (start+2) + " = " + memory.read(start+2));

    }
    
}*/

/*public class Main {

    public static void main(String[] args) {

        Memory memory = new Memory();
        Mars mars = new Mars(memory, 200);

        // ===== Bomber =====
        int startB = 100;
        Warrior bomber = new Warrior(startB, "Bomber");

        Instructions[] progBomber = {
            new Instructions(Opecode.MOV, 2, 5),
            new Instructions(Opecode.JMP, -1, 0),
            new Instructions(Opecode.DAT, 0, 0)
        };

        mars.loadWarrior(startB, progBomber);
        mars.addWarrior(bomber);

        // ===== Runner =====
        int startR = 300;
        Warrior runner = new Warrior(startR, "Runner");

        Instructions[] progRunner = {
            new Instructions(Opecode.JMP, 5, 0),
            new Instructions(Opecode.JMP, -1, 0)
        };

        mars.loadWarrior(startR, progRunner);
        mars.addWarrior(runner);

        Mars.Result r = mars.run();

        if (r.draw) {
            System.out.println("MATCH NUL après " + r.cycles + " cycles");
        } else {
            System.out.println("GAGNANT = " + r.winner.getName() + " après " + r.cycles + " cycles");
        }
    }
}*/

