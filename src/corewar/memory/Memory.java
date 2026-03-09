package corewar.memory;

import corewar.instructions.Instructions;

public class Memory {

    public static final int SIZE = 8000;
    private final Instructions[] arena;

    public Memory() {
        arena = new Instructions[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arena[i] = Instructions.dat();
        }
    }

    private int normalize(int address) { // cest fonction sert a ajouter la notion du memoire circulaire 
        return (address % SIZE + SIZE) % SIZE;
    }

    public int wrap(int address) {
        return normalize(address);
    }

    public Instructions read(int adress){
        return arena[normalize(adress)].copy();
    }

    public void write(int adress, Instructions instruction){
        arena[normalize(adress)] = instruction.copy();
    }   
    
    // On retourne une COPIE de l'instruction stockée en mémoire.
    // Cela évite les effets de bord : le code appelant ne peut pas
    // modifier directement l'instruction contenue dans l'arène.
    // En Corewar, chaque case mémoire doit être indépendante afin
    // d'éviter le partage involontaire d'objets (aliasing mémoire).

    /*`%` c'est le **modulo** = reste de la division. Voilà pourquoi on fait `+SIZE` :
        Sans +SIZE :
        8001 % 8000 =  1   ✅ correct
        -1   % 8000 = -1   ❌ faux ! on voulait 7999

        Avec +SIZE :
        (-1 % 8000 + 8000) % 8000
        = (-1 + 8000) % 8000
        = 7999 % 8000
        = 7999  ✅ correct !*/

    /* normalize est private → accessible seulement dans Memory. wrap est public → accessible depuis Mars et les autres classes. 
        C'est juste une porte d'entrée publique vers normalize. Mars l'utilise pour corriger les adresses :
        javap.pc = memory.wrap(p.pc + 1); // PC+1 mais corrigé si dépasse 7999  */


}