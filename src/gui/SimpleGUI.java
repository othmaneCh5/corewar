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

    // ===== Réglages pour ralentir + rendre la partie longue =====
    private static final int MAX_CYCLES_GUI = 50000;  // match s'arrête au plus tard ici (sécurité)
    private static final int UPDATE_EVERY = 1;        // refresh écran tous les N cycles (plus N est grand, plus c'est fluide)
    private static final int SLEEP_MS = 20;            // pause entre cycles (augmente pour plus lent)

    private Mars mars;
    private Memory memory;
    private Warrior warrior1;
    private Warrior warrior2;
    private Warrior warrior3;

    private Canvas canvas;
    private Label infoLabel;
    private Button playButton;
    private Button stepButton;

    private Thread gameThread;
    private boolean running = false;

    @Override
    public void start(Stage stage) {
        initGame();

        VBox root = new VBox(10);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #1a1a1a;");

        infoLabel = new Label("Ready");
        infoLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-family: monospace;");

        canvas = new Canvas(800, 400);
        drawMemory();

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER);

        playButton = new Button("PLAY");
        playButton.setOnAction(e -> togglePlay());

        stepButton = new Button("STEP");
        stepButton.setOnAction(e -> step());

        Button resetButton = new Button("RESET");
        resetButton.setOnAction(e -> reset());

        buttons.getChildren().addAll(playButton, stepButton, resetButton);
        root.getChildren().addAll(infoLabel, canvas, buttons);

        Scene scene = new Scene(root, 820, 500);
        stage.setTitle("Corewar (Minimal GUI)");
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            running = false;
            if (gameThread != null) gameThread.interrupt();
        });

        update();
    }

    private void initGame() {
        memory = new Memory();

        // maxCycles côté MARS : on met grand pour ne pas limiter (la GUI limite déjà)
        mars = new Mars(memory, 200000);

        // Warriors placés loin l'un de l'autre
        int start1 = 0;
        int start2 = 4000;
        int start3 = 500;

        warrior1 = new Warrior(start1, "Imp");
        warrior2 = new Warrior(start2, "SlowBomber");
        warrior3 = new Warrior(start3, "Imp2");

        // ===== Warrior 1 : IMP =====
        Instructions[] imp = {
            new Instructions(Opecode.MOV, 0, 20),
            new Instructions(Opecode.JMP, 7, 0)
        };

        // ===== Warrior 2 : Bomber lent (fait durer le match mais finit) =====
        // Idée :
        // 1) MOV copie une bombe DAT vers une adresse loin devant (ex: +2000)
        // 2) ADD #1, -1 modifie le champ B du MOV précédent => la cible avance : 2000,2001,2002,...
        // 3) JMP boucle
        // 4) DAT bombe
        //
        // ⚠️ Nécessite ADD implémenté dans ton Mars.
        Instructions[] slowBomber = {
            new Instructions(Opecode.MOV, AddrMode.DIRECT, 3, AddrMode.DIRECT, 200),
            new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 1, AddrMode.DIRECT, 1),
            new Instructions(Opecode.JMP, AddrMode.DIRECT, 8, AddrMode.DIRECT, 0),
            new Instructions(Opecode.DAT, 0, 0)
        };

        Instructions[] imp2 = {
            new Instructions(Opecode.MOV, 0, 5),
            new Instructions(Opecode.JMP, 4, 0)
        };

        // Charger en mémoire
        mars.loadWarrior(start1, imp);
        mars.addWarrior(warrior1);

        mars.loadWarrior(start2, slowBomber);
        mars.addWarrior(warrior2);

        mars.loadWarrior(start3, imp2);
        mars.addWarrior(warrior3);
    }

    private void drawMemory() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, 800, 400);

        int size = Memory.SIZE;

        // affichage en grille 100 colonnes => 80 lignes (8000/100)
        int cols = 100;
        int rows = size / cols;

        double cellW = 800.0 / cols;
        double cellH = 400.0 / rows;

        for (int i = 0; i < size; i++) {
            Instructions inst = memory.read(i);

            Color color;
            switch (inst.opecode) {
                case DAT: color = Color.BLACK; break;
                case MOV: color = Color.LIMEGREEN; break;
                case JMP: color = Color.DODGERBLUE; break;
                case ADD: color = Color.ORANGERED; break;
                default:  color = Color.GRAY; break;
            }

            gc.setFill(color);

            int col = i % cols;
            int row = i / cols;
            gc.fillRect(col * cellW, row * cellH, cellW, cellH);
        }
    }

    private void togglePlay() {
        if (running) {
            running = false;
            playButton.setText("PLAY");
            return;
        }
        running = true;
        playButton.setText("PAUSE");
        play();
    }

    private void play() {
        gameThread = new Thread(() -> {
            while (running
                    && warrior1.isAlive()
                    && warrior2.isAlive()
                    && warrior3.isAlive()
                    && mars.getCycles() < MAX_CYCLES_GUI) {

                mars.step();

                if (mars.getCycles() % UPDATE_EVERY == 0) {
                    Platform.runLater(this::update);
                }

                try {
                    Thread.sleep(SLEEP_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }

            Platform.runLater(() -> {
                running = false;
                playButton.setText("PLAY");
                gameOver();
            });
        });

        gameThread.setDaemon(true);
        gameThread.start();
    }

    private void step() {
        if (!warrior1.isAlive() || !warrior2.isAlive() || mars.getCycles() >= MAX_CYCLES_GUI) {
            gameOver();
            return;
        }

        mars.step();
        update();

        if (!warrior1.isAlive() || !warrior2.isAlive() || mars.getCycles() >= MAX_CYCLES_GUI) {
            gameOver();
        }
    }

    private void reset() {
        running = false;
        playButton.setText("PLAY");
        initGame();
        update();
    }

    private void update() {
        infoLabel.setText(
                "Corewar running | Imp processes=" + warrior1.processes.size() +
                " | Bomber processes=" + warrior2.processes.size() +
                " | Cycle=" + mars.getCycles() +
                " / " + MAX_CYCLES_GUI
        );
        drawMemory();
    }

    private void gameOver() {
        String winner;
        if (mars.getCycles() >= MAX_CYCLES_GUI) {
            winner = "Draw (cycle limit)";
        } else if (warrior1.isAlive() && !warrior2.isAlive()) {
            winner = "Imp";
        } else if (!warrior1.isAlive() && warrior2.isAlive()) {
            winner = "SlowBomber";
        } else {
            winner = "Draw / Nobody";
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
