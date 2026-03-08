package corewar.parser;

// On importe les classes dont on a besoin
import corewar.instructions.AddrMode;    // DIRECT ou IMMEDIATE
import corewar.instructions.Instructions; // une instruction du jeu
import corewar.instructions.Opecode;     // MOV, DAT, ADD, JMP...

import java.io.BufferedReader;  // pour lire un fichier ligne par ligne
import java.io.FileReader;      // pour ouvrir un fichier
import java.io.IOException;     // si le fichier n'existe pas
import java.util.ArrayList;     // liste dynamique (comme un tableau mais qui grandit)
import java.util.List;          // interface de ArrayList

public class RedcodeParser {

    // =========================================================
    // METHODE 1 : lire depuis un FICHIER .red
    // =========================================================
    // "throws IOException" veut dire : si le fichier n'existe pas,
    // Java va signaler une erreur à celui qui appelle cette méthode
    public static Instructions[] parseFile(String path) throws IOException {

        // ArrayList = une liste qui peut grandir
        // On commence avec une liste vide et on ajoute les instructions une par une
        List<Instructions> list = new ArrayList<>();

        // BufferedReader lit un fichier ligne par ligne
        // "try with resources" = Java ferme automatiquement le fichier à la fin
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            // readLine() lit une ligne, retourne null quand c'est la fin du fichier
            while ((line = br.readLine()) != null) {
                // trim() enlève les espaces au début et à la fin : "  MOV 0,1  " → "MOV 0,1"
                Instructions instr = parseLine(line.trim());
                // parseLine retourne null si c'est un commentaire ou ligne vide
                // donc on ajoute seulement si c'est une vraie instruction
                if (instr != null) {
                    list.add(instr);
                }
            }
        }

        // toArray convertit la liste en tableau simple Instructions[]
        return list.toArray(new Instructions[0]);
    }

    // =========================================================
    // METHODE 2 : lire depuis une STRING directement
    // =========================================================
    // Utile pour les tests ou la GUI : pas besoin de fichier
    // Exemple : parseString("MOV 0, 1\nJMP -1, 0")
    public static Instructions[] parseString(String code) {
        List<Instructions> list = new ArrayList<>();

        // split("\n") coupe la string à chaque retour à la ligne
        // "MOV 0,1\nJMP -1,0" → ["MOV 0,1", "JMP -1,0"]
        for (String line : code.split("\n")) {
            Instructions instr = parseLine(line.trim());
            if (instr != null) {
                list.add(instr);
            }
        }

        return list.toArray(new Instructions[0]);
    }

    // =========================================================
    // METHODE 3 : parser UNE seule ligne
    // =========================================================
    // C'est le coeur du parser. Toutes les autres méthodes appellent celle-ci.
    // Retourne null si la ligne ne contient pas d'instruction utile
    public static Instructions parseLine(String line) {

        // Ligne vide → on ignore
        if (line.isEmpty()) return null;

        // Ligne qui commence par ";" → c'est un commentaire → on ignore
        // Exemple : "; name Imp" ou "; ceci est un commentaire"
        if (line.startsWith(";")) return null;

        // Commentaire EN FIN de ligne → on coupe
        // Exemple : "MOV 0, 1 ; copie elle-même" → on garde juste "MOV 0, 1"
        int commentIdx = line.indexOf(';');
        if (commentIdx >= 0) {
            // substring(0, commentIdx) = prend du caractère 0 jusqu'au ";"
            line = line.substring(0, commentIdx).trim();
        }

        // Si après avoir enlevé le commentaire il ne reste rien → on ignore
        if (line.isEmpty()) return null;

        // On sépare l'opcode du reste
        // split("\\s+", 2) = coupe aux espaces, maximum 2 morceaux
        // "ADD #4, 3" → ["ADD", "#4, 3"]
        // "MOV 0, 1"  → ["MOV", "0, 1"]
        String[] parts = line.split("\\s+", 2);

        // Parser l'opcode (le premier mot : MOV, DAT, ADD...)
        // toUpperCase() = met en majuscules pour accepter "mov", "Mov", "MOV"
        Opecode op = parseOpcode(parts[0].toUpperCase());

        // Si l'opcode n'existe pas → on ignore la ligne
        if (op == null) return null;

        // Pas d'opérandes : juste "DAT" sans rien derrière
        if (parts.length == 1) {
            return new Instructions(op, AddrMode.DIRECT, 0, AddrMode.DIRECT, 0);
        }

        // Enlever tous les espaces dans les opérandes
        // "  #4 ,  3  " → "#4,3"
        String operands = parts[1].replaceAll("\\s+", "");

        // Couper au niveau de la virgule → deux opérandes
        // "#4,3" → ["#4", "3"]
        String[] ops = operands.split(",", 2);

        // Une seule opérande : ex "JMP -2"
        if (ops.length == 1) {
            AddrMode modeA = parseMode(ops[0]);  // DIRECT ou IMMEDIATE
            int valA       = parseValue(ops[0]); // la valeur numérique
            return new Instructions(op, modeA, valA, AddrMode.DIRECT, 0);
        }

        // Deux opérandes : ex "MOV #5, 3"
        AddrMode modeA = parseMode(ops[0]);
        int valA       = parseValue(ops[0]);
        AddrMode modeB = parseMode(ops[1]);
        int valB       = parseValue(ops[1]);

        return new Instructions(op, modeA, valA, modeB, valB);
    }

    // =========================================================
    // HELPERS : petites méthodes utilitaires
    // =========================================================

    // Convertit un texte en Opecode
    // "MOV" → Opecode.MOV
    // "DAT" → Opecode.DAT
    // "XYZ" → null (opcode inconnu)
    private static Opecode parseOpcode(String s) {
        try {
            // Opecode.valueOf cherche dans l'enum si "MOV" existe
            return Opecode.valueOf(s);
        } catch (IllegalArgumentException e) {
            // Si l'opcode n'existe pas dans l'enum → on prévient et on retourne null
            System.err.println("[Parser] Opcode inconnu : '" + s + "'");
            return null;
        }
    }

    // Détecte le mode d'adressage à partir du texte
    // "#5"  → IMMEDIATE (valeur brute)
    // "5"   → DIRECT (offset relatif)
    private static AddrMode parseMode(String operand) {
        if (operand.startsWith("#")) return AddrMode.IMMEDIATE;
        return AddrMode.DIRECT;
    }

    // Extrait la valeur numérique d'un opérande
    // "#5"  → 5
    // "3"   → 3
    // "-2"  → -2
    private static int parseValue(String operand) {
        // replaceAll("^[#]", "") enlève le "#" s'il existe au début
        // "^" veut dire "au début de la string"
        // "#5" → "5"   /   "3" → "3"
        String s = operand.replaceAll("^[#]", "");
        try {
            // Integer.parseInt convertit le texte en nombre
            // "5" → 5   /   "-2" → -2
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            System.err.println("[Parser] Valeur invalide : '" + operand + "'");
            return 0;
        }
    }
}
