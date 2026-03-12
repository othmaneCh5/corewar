package gui;

import corewar.instructions.AddrMode;
import corewar.instructions.Instructions;
import corewar.instructions.Opecode;
import corewar.memory.Memory;
import corewar.mars.Mars;
import corewar.warrior.Warrior;
import corewar.parser.RedcodeParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class CorewarGUI extends Application {

 // =========================================================
 // Constants
 // =========================================================
 private static final int MAX_CYCLES = 80000;
 private static final int MEMORY_SIZE = Memory.SIZE;
 private static final int CANVAS_W = 800;
 private static final int CANVAS_H = 400;
 private static final int GRID_COLS = 100;
 private static final int GRID_ROWS = MEMORY_SIZE / GRID_COLS;
 private static final double CELL_W = (double) CANVAS_W / GRID_COLS;
 private static final double CELL_H = (double) CANVAS_H / GRID_ROWS;

 // Warrior colors
 private static final Color[] WARRIOR_COLORS = {
 Color.web("#00ff88"), // W1 green
 Color.web("#ff4466"), // W2 red
 Color.web("#44aaff"), // W3 blue
 Color.web("#ffcc00"), // W4 yellow
 };
 private static final Color COLOR_EMPTY = Color.web("#0d0d0d");
 private static final Color COLOR_RECENT = Color.WHITE;

 // =========================================================
 // Warrior definitions (built-in roster)
 // =========================================================
 private static class WarriorDef {
 final String name;
 final Instructions[] program;
 WarriorDef(String name, Instructions[] program) {
 this.name = name; this.program = program;
 }
 @Override public String toString() { return name; }
 }

 private final List<WarriorDef> roster = new ArrayList<>();

 private void buildRoster() {
 roster.clear();

 // ── IMP ──────────────────────────────────────────────
 // Copies itself one cell forward every cycle.
 // Floods memory linearly. Weak against bombers, strong early.
 roster.add(new WarriorDef("Imp", new Instructions[]{
 new Instructions(Opecode.MOV, 0, 1),
 }));

 // ── DWARF ─────────────────────────────────────────────
 // Blind bomber, step=4. Drops a DAT bomb every 4 cells.
 // ADD increments the B-field of MOV (the target address) by 4 each loop.
 // init_b=7, step=4 => confirmed hits warrior starts at 0/2000/4000/6000.
 roster.add(new WarriorDef("Dwarf", new Instructions[]{
 new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 4, AddrMode.DIRECT, 1),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 2, AddrMode.DIRECT, 7),
 new Instructions(Opecode.JMP, -2, 0),
 new Instructions(Opecode.DAT, 0, 0),
 }));

 // ── QUICKBOMBER ───────────────────────────────────────
 // Aggressive bomber, step=2. Covers twice as many cells as Dwarf.
 // init_b=1, step=2 => confirmed hits all starts.
 roster.add(new WarriorDef("QuickBomber", new Instructions[]{
 new Instructions(Opecode.ADD, AddrMode.IMMEDIATE, 2, AddrMode.DIRECT, 1),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 2, AddrMode.DIRECT, 1),
 new Instructions(Opecode.JMP, -2, 0),
 new Instructions(Opecode.DAT, 0, 0),
 }));

 // ── IMP FACTORY ───────────────────────────────────────
 // SPL loop spawns one forward imp + one backward imp every 3 cycles.
 // Floods memory in both directions. Overwhelms single-process warriors fast.
 // addr 0: SPL 5 => spawn backward imp at offset+5, continue to offset+1
 // addr 1: SPL 3 => spawn forward imp at offset+3, continue to offset+2
 // addr 2: JMP -2 => loop back to keep spawning forever
 // addr 3: MOV 0,1 => forward imp template
 // addr 4: DAT 0,0 => padding (never executed by spawner)
 // addr 5: MOV 0,-1 => backward imp template
 roster.add(new WarriorDef("ImpFactory", new Instructions[]{
 new Instructions(Opecode.SPL, 5, 0),
 new Instructions(Opecode.SPL, 3, 0),
 new Instructions(Opecode.JMP, -2, 0),
 new Instructions(Opecode.MOV, 0, 1),
 new Instructions(Opecode.DAT, 0, 0),
 new Instructions(Opecode.MOV, 0, -1),
 }));

 // ── STONE ─────────────────────────────────────────────
 // Relocates itself 2000 cells ahead then jumps there. Repeats forever.
 // Hard for fixed bombers to kill because it never stays put.
 // JMP offset = 2000 - 5 = 1995 (relative from addr+5 to land at start+2000).
 roster.add(new WarriorDef("Stone", new Instructions[]{
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 4, AddrMode.DIRECT, 2004),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 3, AddrMode.DIRECT, 2003),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 2, AddrMode.DIRECT, 2002),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 1, AddrMode.DIRECT, 2001),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 0, AddrMode.DIRECT, 2000),
 new Instructions(Opecode.JMP, 1995, 0),
 }));

 // ── GEMINI ────────────────────────────────────────────
 // Copies itself to a safe location 3000 cells away, then SPLs a second
 // process there. Original loops to stay alive. Copy runs independently.
 // Two-headed warrior — killing one copy doesn't end the match.
 roster.add(new WarriorDef("Gemini", new Instructions[]{
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 6, AddrMode.DIRECT, 3006),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 5, AddrMode.DIRECT, 3005),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 4, AddrMode.DIRECT, 3004),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 3, AddrMode.DIRECT, 3003),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 2, AddrMode.DIRECT, 3002),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 1, AddrMode.DIRECT, 3001),
 new Instructions(Opecode.MOV, AddrMode.DIRECT, 0, AddrMode.DIRECT, 3000),
 new Instructions(Opecode.SPL, 2993, 0), // spawn at +3000 (offset from addr 7)
 new Instructions(Opecode.JMP, -8, 0), // original loops forever
 }));
 }

 // =========================================================
 // State
 // =========================================================
 private Mars mars;
 private Memory memory;

 // Active warriors in current round (2–4)
 private final List<Warrior> activeWarriors = new ArrayList<>();
 private final List<WarriorDef> activeDefs = new ArrayList<>();

 // Ownership + recent-write tracking
 private final int[] ownership = new int[MEMORY_SIZE]; // 0=empty, 1..4=warrior index
 private final long[] lastWritten = new long[MEMORY_SIZE]; // cycle when cell was last written
 private long currentCycle = 0;

 // Tournament
 private int tournamentRounds = 5;
 private int currentRound = 0;
 private final Map<String, Integer> scores = new LinkedHashMap<>();
 private final List<String> roundHistory = new ArrayList<>();
 private boolean inTournament = false;

 // Threading
 private Thread gameThread;
 private volatile boolean running = false;

 // UI
 private Canvas canvas;
 private Label cycleLabel;
 private Label roundLabel;
 private Button playButton;
 private VBox statsPanel;
 private VBox historyPanel;
 private Slider speedSlider;
 private Label speedLabel;
 private ComboBox<Integer> roundsCombo;

 // Warrior slot selectors (up to 4)
 private final ComboBox<Object>[] slotBoxes = new ComboBox[4];
 private final CheckBox[] slotActive = new CheckBox[4];

 private Stage primaryStage;

 // =========================================================
 // JavaFX entry
 // =========================================================
 @Override
 public void start(Stage stage) {
 this.primaryStage = stage;
 buildRoster();

 BorderPane root = new BorderPane();
 root.setStyle("-fx-background-color: #111111;");

 // TOP — title + round info
 root.setTop(buildTopBar());

 // CENTER — canvas
 canvas = new Canvas(CANVAS_W, CANVAS_H);
 StackPane canvasPane = new StackPane(canvas);
 canvasPane.setStyle("-fx-background-color: #000000;");
 canvasPane.setPadding(new Insets(6));
 root.setCenter(canvasPane);

 // LEFT — warrior slots
 root.setLeft(buildSlotPanel());

 // RIGHT — live stats + round history
 root.setRight(buildRightPanel());

 // BOTTOM — controls
 root.setBottom(buildControlBar());

 Scene scene = new Scene(root, 1200, 650);
 stage.setTitle("⚔ Corewar Arena");
 stage.setScene(scene);
 stage.show();

 stage.setOnCloseRequest(e -> {
 running = false;
 if (gameThread != null) gameThread.interrupt();
 });

 initRound();
 drawMemory();
 }

 // =========================================================
 // UI builders
 // =========================================================

 private HBox buildTopBar() {
 cycleLabel = new Label("Cycle 0 / " + MAX_CYCLES);
 cycleLabel.setStyle("-fx-text-fill: #aaffaa; -fx-font-size: 14px; -fx-font-family: monospace;");

 roundLabel = new Label("Round 1");
 roundLabel.setStyle("-fx-text-fill: #ffcc44; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: monospace;");

 Label title = new Label("⚔ COREWAR ARENA");
 title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-font-family: monospace;");

 HBox bar = new HBox(30, roundLabel, title, cycleLabel);
 bar.setAlignment(Pos.CENTER);
 bar.setPadding(new Insets(8));
 bar.setStyle("-fx-background-color: #1a1a1a;");
 return bar;
 }

 @SuppressWarnings("unchecked")
 private VBox buildSlotPanel() {
 VBox panel = new VBox(10);
 panel.setPadding(new Insets(10));
 panel.setStyle("-fx-background-color: #181818;");
 panel.setPrefWidth(200);

 Label title = new Label("WARRIORS");
 title.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");
 panel.getChildren().add(title);

 for (int i = 0; i < 4; i++) {
 final int idx = i;

 // Active checkbox
 slotActive[i] = new CheckBox();
 slotActive[i].setSelected(i < 2); // first two active by default
 slotActive[i].setStyle("-fx-text-fill: white;");

 // Warrior selector
 slotBoxes[i] = new ComboBox<>();
 for (WarriorDef wd : roster) slotBoxes[i].getItems().add(wd);
 slotBoxes[i].getItems().add("\uD83D\uDCC2 Load .red file...");
 slotBoxes[i].getSelectionModel().select(i < roster.size() ? i : 0);
 slotBoxes[i].setPrefWidth(145);
 slotBoxes[i].setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

 // Handle file picker
 slotBoxes[i].setOnAction(e -> {
 Object sel = slotBoxes[idx].getSelectionModel().getSelectedItem();
 if ("\uD83D\uDCC2 Load .red file...".equals(sel)) {
 loadRedFile(idx);
 }
 });

 // Color dot
 Label dot = new Label("●");
 dot.setStyle("-fx-text-fill: " + toHex(WARRIOR_COLORS[i]) + "; -fx-font-size: 16px;");

 HBox row = new HBox(5, slotActive[i], dot, slotBoxes[i]);
 row.setAlignment(Pos.CENTER_LEFT);
 panel.getChildren().add(row);
 }

 // Separator
 Separator sep = new Separator();
 sep.setStyle("-fx-background-color: #333;");
 panel.getChildren().add(sep);

 // Tournament settings
 Label tLabel = new Label("TOURNAMENT");
 tLabel.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");

 roundsCombo = new ComboBox<>();
 roundsCombo.getItems().addAll(1, 3, 5, 10, 20);
 roundsCombo.getSelectionModel().select(2); // default 5
 roundsCombo.setStyle("-fx-font-family: monospace;");

 HBox tRow = new HBox(8, new Label("Rounds:") {{
 setStyle("-fx-text-fill: white; -fx-font-family: monospace; -fx-font-size: 12px;");
 }}, roundsCombo);
 tRow.setAlignment(Pos.CENTER_LEFT);

 Button startTournament = new Button("▶ START TOURNAMENT");
 startTournament.setStyle(btnStyle("#ffcc00", "#000"));
 startTournament.setMaxWidth(Double.MAX_VALUE);
 startTournament.setOnAction(e -> startTournament());

 panel.getChildren().addAll(tLabel, tRow, startTournament);

 return panel;
 }

 private VBox buildRightPanel() {
 // Live stats
 statsPanel = new VBox(6);
 statsPanel.setPadding(new Insets(8));

 Label statsTitle = new Label("LIVE STATS");
 statsTitle.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");
 statsPanel.getChildren().add(statsTitle);

 // History
 historyPanel = new VBox(4);
 historyPanel.setPadding(new Insets(8));

 Label histTitle = new Label("ROUND HISTORY");
 histTitle.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");
 historyPanel.getChildren().add(histTitle);

 VBox right = new VBox(10, statsPanel, new Separator(), historyPanel);
 right.setStyle("-fx-background-color: #181818;");
 right.setPrefWidth(190);
 right.setPadding(new Insets(10));

 return right;
 }

 private HBox buildControlBar() {
 playButton = new Button("▶ PLAY");
 playButton.setStyle(btnStyle("#00ff88", "#000"));
 playButton.setOnAction(e -> togglePlay());

 Button stepButton = new Button("⏭ STEP");
 stepButton.setStyle(btnStyle("#44aaff", "#000"));
 stepButton.setOnAction(e -> step());

 Button step10Button = new Button("⏭×10");
 step10Button.setStyle(btnStyle("#44aaff", "#000"));
 step10Button.setOnAction(e -> stepN(10));

 Button step100Button = new Button("⏭×100");
 step100Button.setStyle(btnStyle("#44aaff", "#000"));
 step100Button.setOnAction(e -> stepN(100));

 Button resetButton = new Button("↺ RESET");
 resetButton.setStyle(btnStyle("#ff4466", "#fff"));
 resetButton.setOnAction(e -> reset());

 // Speed slider
 speedSlider = new Slider(0, 5, 2);
 speedSlider.setMajorTickUnit(1);
 speedSlider.setSnapToTicks(true);
 speedSlider.setPrefWidth(160);
 speedSlider.setStyle("-fx-control-inner-background: #333;");

 speedLabel = new Label(speedLabel(speedSlider.getValue()));
 speedLabel.setStyle("-fx-text-fill: white; -fx-font-family: monospace; -fx-font-size: 12px;");
 speedLabel.setMinWidth(80);

 speedSlider.valueProperty().addListener((obs, ov, nv) ->
 speedLabel.setText(speedLabel(nv.doubleValue())));

 Label spdTitle = new Label("SPEED:");
 spdTitle.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 12px;");

 HBox bar = new HBox(10,
 playButton, stepButton, step10Button, step100Button, resetButton,
 new Separator() {{ setOrientation(javafx.geometry.Orientation.VERTICAL); }},
 spdTitle, speedSlider, speedLabel
 );
 bar.setAlignment(Pos.CENTER);
 bar.setPadding(new Insets(8));
 bar.setStyle("-fx-background-color: #1a1a1a;");
 return bar;
 }

 private String speedLabel(double val) {
 return switch ((int) Math.round(val)) {
 case 0 -> "SLOWEST";
 case 1 -> "SLOW";
 case 2 -> "NORMAL";
 case 3 -> "FAST";
 case 4 -> "FASTER";
 case 5 -> "MAX";
 default -> "?";
 };
 }

 // Sleep ms per cycle based on speed slider (0=slowest, 5=max)
 private int sleepMs() {
 return switch ((int) Math.round(speedSlider.getValue())) {
 case 0 -> 200;
 case 1 -> 50;
 case 2 -> 15;
 case 3 -> 5;
 case 4 -> 1;
 case 5 -> 0;
 default -> 15;
 };
 }

 // How many cycles to run per UI tick at max speed
 private int cyclesPerTick() {
 return (int) Math.round(speedSlider.getValue()) == 5 ? 50 : 1;
 }

 // =========================================================
 // Game init
 // =========================================================

 private void initRound() {
 memory = new Memory();
 mars = new Mars(memory, MAX_CYCLES);
 activeWarriors.clear();
 activeDefs.clear();
 Arrays.fill(ownership, 0);
 Arrays.fill(lastWritten, -999);
 currentCycle = 0;

 // Gather active warrior slots
 List<WarriorDef> chosen = new ArrayList<>();
 for (int i = 0; i < 4; i++) {
 if (slotActive[i].isSelected()) {
 Object sel = slotBoxes[i].getSelectionModel().getSelectedItem();
 if (sel instanceof WarriorDef wd) chosen.add(wd);
 }
 }
 if (chosen.size() < 2) {
 // fallback: use first two
 chosen.clear();
 chosen.add(roster.get(0));
 chosen.add(roster.get(1));
 }

 // Space warriors evenly around the arena
 int spacing = MEMORY_SIZE / chosen.size();
 for (int i = 0; i < chosen.size(); i++) {
 WarriorDef def = chosen.get(i);
 int startPc = i * spacing;
 Warrior w = new Warrior(startPc, def.name);
 mars.loadWarrior(startPc, def.program);
 mars.addWarrior(w);
 activeWarriors.add(w);
 activeDefs.add(def);
 // Mark initial ownership
 for (int j = 0; j < def.program.length; j++) {
 ownership[startPc + j] = i + 1;
 }
 }
 }

 // =========================================================
 // Drawing
 // =========================================================

 private void drawMemory() {
 GraphicsContext gc = canvas.getGraphicsContext2D();
 gc.setFill(Color.BLACK);
 gc.fillRect(0, 0, CANVAS_W, CANVAS_H);

 for (int i = 0; i < MEMORY_SIZE; i++) {
 Instructions inst = memory.read(i);
 boolean empty = inst.opecode == Opecode.DAT
 && inst.parametreA == 0
 && inst.parametreB == 0;

 Color base;
 if (empty) {
 base = COLOR_EMPTY;
 } else {
 int owner = ownership[i];
 base = owner >= 1 && owner <= 4
 ? WARRIOR_COLORS[owner - 1]
 : Color.web("#666666");
 }

 // Flash recently written cells
 long age = currentCycle - lastWritten[i];
 if (age < 3) {
 base = (Color) base.interpolate(COLOR_RECENT, 1.0 - age / 3.0);
 }

 gc.setFill(base);
 int col = i % GRID_COLS;
 int row = i / GRID_COLS;
 gc.fillRect(col * CELL_W, row * CELL_H, CELL_W - 0.3, CELL_H - 0.3);
 }

 // Draw process PC markers
 for (int wi = 0; wi < activeWarriors.size(); wi++) {
 Warrior w = activeWarriors.get(wi);
 if (!w.isAlive()) continue;
 gc.setFill(Color.WHITE);
 for (var p : w.getProcesses()) {
 int i = p.getPc() % MEMORY_SIZE;
 int col = i % GRID_COLS;
 int row = i / GRID_COLS;
 gc.fillOval(
 col * CELL_W + CELL_W * 0.25,
 row * CELL_H + CELL_H * 0.25,
 CELL_W * 0.5, CELL_H * 0.5
 );
 }
 }
 }

 // =========================================================
 // Stats panel update
 // =========================================================

 private void updateStats() {
 statsPanel.getChildren().clear();

 Label title = new Label("LIVE STATS");
 title.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");
 statsPanel.getChildren().add(title);

 // Count cells owned by each warrior
 int[] cellsOwned = new int[activeWarriors.size()];
 for (int o : ownership) {
 if (o >= 1 && o <= activeWarriors.size()) cellsOwned[o - 1]++;
 }

 for (int i = 0; i < activeWarriors.size(); i++) {
 Warrior w = activeWarriors.get(i);
 Color c = WARRIOR_COLORS[i];

 String status = w.isAlive()
 ? w.getProcesses().size() + " proc " + cellsOwned[i] + " cells"
 : "\uD83D\uDC80 DEAD";

 Label name = new Label(w.getName());
 name.setStyle("-fx-text-fill: " + toHex(c) + "; -fx-font-family: monospace; -fx-font-weight: bold; -fx-font-size: 12px;");

 Label stat = new Label(status);
 stat.setStyle("-fx-text-fill: " + (w.isAlive() ? "#cccccc" : "#ff4444")
 + "; -fx-font-family: monospace; -fx-font-size: 11px;");

 // Mini progress bar for cells owned
 double pct = (double) cellsOwned[i] / MEMORY_SIZE;
 ProgressBar bar = new ProgressBar(pct);
 bar.setPrefWidth(160);
 bar.setStyle("-fx-accent: " + toHex(c) + ";");

 statsPanel.getChildren().addAll(name, stat, bar);
 }

 // Tournament scores
 if (inTournament && !scores.isEmpty()) {
 statsPanel.getChildren().add(new Separator());
 Label scTitle = new Label("SCORES");
 scTitle.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");
 statsPanel.getChildren().add(scTitle);
 scores.forEach((name, score) -> {
 Label l = new Label(name + ": " + score + " pts");
 l.setStyle("-fx-text-fill: #ffcc44; -fx-font-family: monospace; -fx-font-size: 12px;");
 statsPanel.getChildren().add(l);
 });
 }
 }

 private void updateHistory() {
 historyPanel.getChildren().clear();
 Label title = new Label("ROUND HISTORY");
 title.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 11px;");
 historyPanel.getChildren().add(title);

 // Show last 10 rounds
 int start = Math.max(0, roundHistory.size() - 10);
 for (int i = start; i < roundHistory.size(); i++) {
 Label l = new Label(roundHistory.get(i));
 l.setStyle("-fx-text-fill: #cccccc; -fx-font-family: monospace; -fx-font-size: 11px;");
 l.setWrapText(true);
 historyPanel.getChildren().add(l);
 }
 }

 // =========================================================
 // Step execution with ownership tracking
 // =========================================================

 private void doStep() {
 // Snapshot memory checksums to detect writes — too slow for 8000 cells.
 // Instead, intercept via a lightweight scan of cells that changed.
 // We track ownership by rescanning non-empty cells after each step.
 mars.step();
 currentCycle = mars.getCycles();
 refreshOwnership();
 }

 private void refreshOwnership() {
 // Heuristic: nearest warrior start owns any non-empty cell.
 // For single-process warriors this is accurate.
 // We also mark recently written cells via lastWritten.

 int[] starts = new int[activeWarriors.size()];
 for (int i = 0; i < activeWarriors.size(); i++) {
 starts[i] = activeWarriors.get(i).isAlive()
 ? activeWarriors.get(i).getStartPc()
 : -1;
 }

 for (int i = 0; i < MEMORY_SIZE; i++) {
 Instructions inst = memory.read(i);
 boolean empty = inst.opecode == Opecode.DAT
 && inst.parametreA == 0
 && inst.parametreB == 0;

 if (empty) {
 if (ownership[i] != 0) {
 ownership[i] = 0;
 lastWritten[i] = currentCycle;
 }
 continue;
 }

 int best = 0, bestDist = Integer.MAX_VALUE;
 for (int w = 0; w < activeWarriors.size(); w++) {
 if (starts[w] < 0) continue;
 int dist = Math.min(
 Math.abs(i - starts[w]),
 MEMORY_SIZE - Math.abs(i - starts[w])
 );
 if (dist < bestDist) { bestDist = dist; best = w + 1; }
 }

 if (ownership[i] != best) {
 ownership[i] = best;
 lastWritten[i] = currentCycle;
 }
 }
 }

 // =========================================================
 // Game over check
 // =========================================================

 private boolean isRoundOver() {
 long alive = activeWarriors.stream().filter(Warrior::isAlive).count();
 return alive <= 1 || mars.getCycles() >= MAX_CYCLES;
 }

 private Warrior getWinner() {
 return activeWarriors.stream()
 .filter(Warrior::isAlive)
 .findFirst()
 .orElse(null);
 }

 // =========================================================
 // Controls
 // =========================================================

 private void togglePlay() {
 if (running) {
 running = false;
 playButton.setText("▶ PLAY");
 playButton.setStyle(btnStyle("#00ff88", "#000"));
 return;
 }
 running = true;
 playButton.setText("⏸ PAUSE");
 playButton.setStyle(btnStyle("#ffcc00", "#000"));
 play();
 }

 private void play() {
 gameThread = new Thread(() -> {
 while (running && !isRoundOver()) {
 int n = cyclesPerTick();
 for (int i = 0; i < n && !isRoundOver(); i++) {
 doStep();
 }
 Platform.runLater(this::updateUI);

 int sleep = sleepMs();
 if (sleep > 0) {
 try { Thread.sleep(sleep); }
 catch (InterruptedException e) { break; }
 }
 }

 Platform.runLater(() -> {
 running = false;
 playButton.setText("▶ PLAY");
 playButton.setStyle(btnStyle("#00ff88", "#000"));
 updateUI();
 if (isRoundOver()) onRoundOver();
 });
 });
 gameThread.setDaemon(true);
 gameThread.start();
 }

 private void step() {
 if (isRoundOver()) { onRoundOver(); return; }
 doStep();
 updateUI();
 if (isRoundOver()) onRoundOver();
 }

 private void stepN(int n) {
 if (isRoundOver()) { onRoundOver(); return; }
 for (int i = 0; i < n && !isRoundOver(); i++) doStep();
 updateUI();
 if (isRoundOver()) onRoundOver();
 }

 private void reset() {
 running = false;
 if (gameThread != null) gameThread.interrupt();
 playButton.setText("▶ PLAY");
 playButton.setStyle(btnStyle("#00ff88", "#000"));
 inTournament = false;
 currentRound = 0;
 scores.clear();
 roundHistory.clear();
 roundLabel.setText("Round 1");
 initRound();
 updateUI();
 }

 private void updateUI() {
 cycleLabel.setText("Cycle " + mars.getCycles() + " / " + MAX_CYCLES);
 drawMemory();
 updateStats();
 updateHistory();
 }

 // =========================================================
 // Round over handler
 // =========================================================

 private void onRoundOver() {
 Warrior winner = getWinner();
 String winnerName = winner != null ? winner.getName() : "Draw";

 // Record history entry
 String entry = "R" + (currentRound + 1) + ": "
 + winnerName
 + " (" + mars.getCycles() + "c)";
 roundHistory.add(entry);

 // Award points
 if (winner != null) {
 scores.merge(winner.getName(), 1, Integer::sum);
 }

 if (inTournament) {
 currentRound++;
 if (currentRound < tournamentRounds) {
 roundLabel.setText("Round " + (currentRound + 1) + " / " + tournamentRounds);
 updateHistory();
 updateStats();
 // Short delay then auto-start next round
 new Thread(() -> {
 try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
 Platform.runLater(() -> {
 initRound();
 updateUI();
 // auto-play next round
 running = true;
 playButton.setText("⏸ PAUSE");
 playButton.setStyle(btnStyle("#ffcc00", "#000"));
 play();
 });
 }) {{ setDaemon(true); start(); }};
 } else {
 // Tournament over
 inTournament = false;
 updateHistory();
 updateStats();
 showTournamentResult();
 }
 } else {
 // Single match over
 showMatchResult(winnerName);
 }
 }

 private void showMatchResult(String winnerName) {
 Alert alert = new Alert(Alert.AlertType.INFORMATION);
 alert.setTitle("Round Over");
 alert.setHeaderText("Winner: " + winnerName);
 alert.setContentText("Cycles: " + mars.getCycles()
 + "\n\nPress RESET to play again or START TOURNAMENT for a full series.");
 alert.showAndWait();
 }

 private void showTournamentResult() {
 // Find champion
 String champion = scores.entrySet().stream()
 .max(Map.Entry.comparingByValue())
 .map(Map.Entry::getKey)
 .orElse("Nobody");

 StringBuilder sb = new StringBuilder();
 sb.append("\uD83C\uDFC6 CHAMPION: ").append(champion).append("\n\n");
 sb.append("Final Scores:\n");
 scores.entrySet().stream()
 .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
 .forEach(e -> sb.append(" ").append(e.getKey())
 .append(": ").append(e.getValue())
 .append(" / ").append(tournamentRounds).append("\n"));

 Alert alert = new Alert(Alert.AlertType.INFORMATION);
 alert.setTitle("Tournament Over");
 alert.setHeaderText("Tournament Complete — " + tournamentRounds + " rounds");
 alert.setContentText(sb.toString());
 alert.showAndWait();
 }

 // =========================================================
 // Tournament start
 // =========================================================

 private void startTournament() {
 running = false;
 if (gameThread != null) gameThread.interrupt();

 tournamentRounds = roundsCombo.getValue();
 currentRound = 0;
 inTournament = true;
 scores.clear();
 roundHistory.clear();

 roundLabel.setText("Round 1 / " + tournamentRounds);
 initRound();
 updateUI();

 // Auto-play
 running = true;
 playButton.setText("⏸ PAUSE");
 playButton.setStyle(btnStyle("#ffcc00", "#000"));
 play();
 }

 // =========================================================
 // Load .red file
 // =========================================================

 private void loadRedFile(int slotIndex) {
 FileChooser fc = new FileChooser();
 fc.setTitle("Open Redcode Warrior");
 fc.getExtensionFilters().add(
 new FileChooser.ExtensionFilter("Redcode files", "*.red", "*.txt")
 );
 File file = fc.showOpenDialog(primaryStage);
 if (file == null) {
 // User cancelled — revert to previous selection
 slotBoxes[slotIndex].getSelectionModel().select(0);
 return;
 }
 try {
 Instructions[] program = RedcodeParser.parseFile(file.getAbsolutePath());
 String name = file.getName().replace(".red", "").replace(".txt", "");
 WarriorDef newDef = new WarriorDef(name, program);
 roster.add(newDef);
 // Add to all combo boxes
 for (ComboBox<Object> box : slotBoxes) {
 int last = box.getItems().size() - 1; // before "Load file..."
 box.getItems().add(last, newDef);
 }
 slotBoxes[slotIndex].getSelectionModel().select(newDef);
 } catch (Exception ex) {
 Alert err = new Alert(Alert.AlertType.ERROR);
 err.setTitle("Parse Error");
 err.setHeaderText("Could not load warrior");
 err.setContentText(ex.getMessage());
 err.showAndWait();
 slotBoxes[slotIndex].getSelectionModel().select(0);
 }
 }

 // =========================================================
 // Helpers
 // =========================================================

 private String btnStyle(String bg, String fg) {
 return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; "
 + "-fx-font-weight: bold; -fx-font-family: monospace; -fx-cursor: hand; "
 + "-fx-font-size: 12px;";
 }

 private String toHex(Color c) {
 return String.format("#%02x%02x%02x",
 (int)(c.getRed() * 255),
 (int)(c.getGreen() * 255),
 (int)(c.getBlue() * 255));
 }

 public static void main(String[] args) {
 launch(args);
 }
}