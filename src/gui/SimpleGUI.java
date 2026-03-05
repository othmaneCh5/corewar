package gui;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.instructions.Opecode;
import corewar.memory.Memory;
import corewar.mars.Mars;
import corewar.warrior.Warrior;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class SimpleGUI extends Application {

    private static final int MAX_CYCLES_GUI = 50000;
    private static final int UPDATE_EVERY   = 1;
    private static final int SLEEP_MS       = 20;

    private Mars    mars;
    private Memory  memory;
    private Warrior warrior1; // Imp
    private Warrior warrior2; // SlowBomber
    private Warrior warrior3; // Imp2

    // Ownership map: which warrior last wrote each cell (0=nobody, 1/2/3=warrior)
    private final int[] ownership = new int[Memory.SIZE];

    private Canvas canvas;
    private Label  infoLabel;
    private Button playButton;

    private Thread  gameThread;
    private boolean running = false;

    @Override
    public void start(Stage stage) {
        initGame();

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #1a1a1a;");

        infoLabel = new Label("Ready");
        infoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-family: monospace;");

        canvas = new Canvas(800, 400);
        drawMemory();

        // Legend
        HBox legend = new HBox(20);
        legend.setAlignment(Pos.CENTER);
        legend.getChildren().addAll(
            legendBox("#00ff88", "Imp (W1)"),
            legendBox("#ff4466", "SlowBomber (W2)"),
            legendBox("#44aaff", "Imp2 (W3)"),
            legendBox("#333333", "Empty (DAT)")
        );

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        playButton = new Button("▶ PLAY");
        playButton.setStyle(btnStyle("#00ff88"));
        playButton.setOnAction(e -> togglePlay());

        Button stepButton = new Button("⏭ STEP");
        stepButton.setStyle(btnStyle("#44aaff"));
        stepButton.setOnAction(e -> step());

        Button resetButton = new Button("↺ RESET");
        resetButton.setStyle(btnStyle("#ff4466"));
        resetButton.setOnAction(e -> reset());

        buttons.getChildren().addAll(playButton, stepButton, resetButton);
        root.getChildren().addAll(infoLabel, canvas, legend, buttons);

        Scene scene = new Scene(root, 820, 530);
        stage.setTitle("⚔ Corewar");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            running = false;
            if (gameThread != null) gameThread.interrupt();
        });

        update();
    }

    private javafx.scene.layout.HBox legendBox(String hex, String text) {
        javafx.scene.shape.Rectangle rect = new javafx.scene.shape.Rectangle(16, 16);
        rect.setFill(Color.web(hex));
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-family: monospace;");
        javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(6, rect, lbl);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private String btnStyle(String color) {
        return "-fx-background-color: " + color + "; -fx-text-fill: black; " +
               "-fx-font-weight: bold; -fx-font-family: monospace; -fx-cursor: hand;";
    }

    private void initGame() {
        memory = new Memory();
        mars   = new Mars(memory, 200000);

        // Reset ownership map
        java.util.Arrays.fill(ownership, 0);

        int start1 = 0;
        int start2 = 3000;
        int start3 = 6000;

        warrior1 = new Warrior(start1, "Imp");
        warrior2 = new Warrior(start2, "SlowBomber");
        warrior3 = new Warrior(start3, "Imp2");

        // ===== Warrior 1 : Classic IMP =====
        // MOV 0,1 : copies itself to the next cell, PC advances by 1 => repeats forever
        // A single-instruction self-replicating program.
        Instructions[] imp = {
            new Instructions(Opecode.MOV, 0, 1),
        };

        // ===== Warrior 2 : Slow Bomber =====
        // addr 0 : MOV the DAT bomb (offset +3) to target (B starts at 200, advances by 1 each loop)
        // addr 1 : ADD #1 to the B field of addr 0 => target address advances by 1 each loop
        // addr 2 : JMP -2 => back to addr 0
        // addr 3 : DAT bomb (source only, never executed)
        Instructions[] slowBomber = {
            new Instructions(Opecode.MOV, AddrMode.DIRECT, 3, AddrMode.DIRECT, 200),
            new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 1, AddrMode.DIRECT, -1),
            new Instructions(Opecode.JMP, -2, 0),
            new Instructions(Opecode.DAT, 0, 0),
        };

        // ===== Warrior 3 : Imp variant (larger stride) =====
        // MOV 0,2 : copies itself 2 cells ahead, effectively jumping by 2 each cycle
        Instructions[] imp2 = {
            new Instructions(Opecode.MOV, 0, 2),
        };

        mars.loadWarrior(start1, imp);
        mars.addWarrior(warrior1);

        mars.loadWarrior(start2, slowBomber);
        mars.addWarrior(warrior2);

        mars.loadWarrior(start3, imp2);
        mars.addWarrior(warrior3);

        // Mark initial ownership
        for (int i = 0; i < imp.length;        i++) ownership[start1 + i] = 1;
        for (int i = 0; i < slowBomber.length; i++) ownership[start2 + i] = 2;
        for (int i = 0; i < imp2.length;       i++) ownership[start3 + i] = 3;
    }

    // ── drawing ──────────────────────────────────────────────────────────────

    private void drawMemory() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, 800, 400);

        int cols  = 100;
        int rows  = Memory.SIZE / cols;
        double cw = 800.0 / cols;
        double ch = 400.0 / rows;

        for (int i = 0; i < Memory.SIZE; i++) {
            Instructions inst = memory.read(i);
            Color color = cellColor(inst, ownership[i]);
            gc.setFill(color);
            int col = i % cols;
            int row = i / cols;
            gc.fillRect(col * cw, row * ch, cw - 0.5, ch - 0.5);
        }

        // Draw process position markers (bright dot)
        drawProcessMarker(gc, warrior1, Color.WHITE,   cw, ch, cols);
        drawProcessMarker(gc, warrior2, Color.YELLOW,  cw, ch, cols);
        drawProcessMarker(gc, warrior3, Color.MAGENTA, cw, ch, cols);
    }

    private Color cellColor(Instructions inst, int owner) {
        if (inst.opecode == Opecode.DAT && inst.parametreA == 0 && inst.parametreB == 0) {
            return Color.web("#111111"); // empty cell
        }
        // Base color by owner, brightness by opcode
        return switch (owner) {
            case 1  -> Color.web("#00ff88"); // Imp: green
            case 2  -> Color.web("#ff4466"); // Bomber: red
            case 3  -> Color.web("#44aaff"); // Imp2: blue
            default -> Color.web("#888888"); // unknown / overwritten
        };
    }

    private void drawProcessMarker(GraphicsContext gc, Warrior w, Color c,
                                   double cw, double ch, int cols) {
        if (!w.isAlive()) return;
        gc.setFill(c);
        for (var p : w.getProcesses()) {
            int i   = p.getPc();
            int col = i % cols;
            int row = i / cols;
            // Small bright dot in center of cell
            gc.fillOval(col * cw + cw * 0.2, row * ch + ch * 0.2, cw * 0.6, ch * 0.6);
        }
    }

    // ── game loop ─────────────────────────────────────────────────────────────

    private boolean isGameOver() {
        int alive = (warrior1.isAlive() ? 1 : 0)
                  + (warrior2.isAlive() ? 1 : 0)
                  + (warrior3.isAlive() ? 1 : 0);
        return alive <= 1 || mars.getCycles() >= MAX_CYCLES_GUI;
    }

    private void togglePlay() {
        if (running) {
            running = false;
            playButton.setText("▶ PLAY");
            return;
        }
        running = true;
        playButton.setText("⏸ PAUSE");
        play();
    }

    private void play() {
        gameThread = new Thread(() -> {
            while (running && !isGameOver()) {
                doStep();

                if (mars.getCycles() % UPDATE_EVERY == 0) {
                    Platform.runLater(this::update);
                }

                try { Thread.sleep(SLEEP_MS); }
                catch (InterruptedException e) { break; }
            }

            Platform.runLater(() -> {
                running = false;
                playButton.setText("▶ PLAY");
                update();
                if (isGameOver()) gameOver();
            });
        });

        gameThread.setDaemon(true);
        gameThread.start();
    }

    private void step() {
        if (isGameOver()) { gameOver(); return; }
        doStep();
        update();
        if (isGameOver()) gameOver();
    }

    /** Execute one MARS step and update ownership map based on what changed. */
    private void doStep() {
        // Snapshot memory before step to detect writes
        // (lightweight: just re-read changed cells after step)
        mars.step();
        // Recolor cells owned by the warrior whose process just ran.
        // Since we can't easily intercept writes, we repaint based on
        // non-DAT cells that weren't owned before — approximate but visual.
        refreshOwnership();
    }

    /**
     * Scan all non-empty cells and assign ownership based on proximity to warrior start.
     * Simple heuristic: nearest warrior start (circular distance) owns the cell.
     * Good enough for visualization.
     */
    private void refreshOwnership() {
        int[] starts = {
            warrior1.isAlive() ? warrior1.getStartPc() : -1,
            warrior2.isAlive() ? warrior2.getStartPc() : -1,
            warrior3.isAlive() ? warrior3.getStartPc() : -1,
        };

        for (int i = 0; i < Memory.SIZE; i++) {
            Instructions inst = memory.read(i);
            boolean empty = inst.opecode == Opecode.DAT
                         && inst.parametreA == 0
                         && inst.parametreB == 0;
            if (empty) {
                ownership[i] = 0;
                continue;
            }
            // Find nearest alive warrior (circular)
            int best = 0, bestDist = Integer.MAX_VALUE;
            for (int w = 0; w < 3; w++) {
                if (starts[w] < 0) continue;
                int dist = Math.min(
                    Math.abs(i - starts[w]),
                    Memory.SIZE - Math.abs(i - starts[w])
                );
                if (dist < bestDist) { bestDist = dist; best = w + 1; }
            }
            ownership[i] = best;
        }
    }

    private void reset() {
        running = false;
        if (gameThread != null) gameThread.interrupt();
        playButton.setText("▶ PLAY");
        initGame();
        update();
    }

    private void update() {
        String w1 = warrior1.isAlive()
            ? "Imp=" + warrior1.getProcesses().size() + "p"
            : "Imp=DEAD";
        String w2 = warrior2.isAlive()
            ? "Bomber=" + warrior2.getProcesses().size() + "p"
            : "Bomber=DEAD";
        String w3 = warrior3.isAlive()
            ? "Imp2=" + warrior3.getProcesses().size() + "p"
            : "Imp2=DEAD";

        infoLabel.setText(
            "Cycle " + mars.getCycles() + "/" + MAX_CYCLES_GUI
            + "  |  " + w1 + "  |  " + w2 + "  |  " + w3
        );
        drawMemory();
    }

    private void gameOver() {
        int alive = (warrior1.isAlive() ? 1 : 0)
                  + (warrior2.isAlive() ? 1 : 0)
                  + (warrior3.isAlive() ? 1 : 0);

        String winner;
        if (mars.getCycles() >= MAX_CYCLES_GUI || alive > 1) {
            winner = "Draw (cycle limit)";
        } else if (warrior1.isAlive()) {
            winner = "🏆 Imp";
        } else if (warrior2.isAlive()) {
            winner = "🏆 SlowBomber";
        } else if (warrior3.isAlive()) {
            winner = "🏆 Imp2";
        } else {
            winner = "Draw (all dead)";
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Over");
        alert.setHeaderText("Winner: " + winner);
        alert.setContentText("Cycles: " + mars.getCycles());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}