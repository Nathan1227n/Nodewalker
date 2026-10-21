package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import javafx.application.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Operational laboratory; generation, search, validation and world preparation run on workers. */
public final class NodeWalkerApp extends Application {
    private TspWorldView world;
    private OperationalController controller;
    private OperationalPanel panel;
    private WorldSceneModel model;
    private Scene scene;
    private final ComboBox<String> refuges = new ComboBox<>();
    private final Label coordinates = new Label("Selecione um refúgio"), mode = new Label(), mapTitle = new Label("ÚLTIMOS REFÚGIOS"), mapSubtitle = new Label("Configure N e seed; clique GERAR.");
    private CheckBox decoration, simplified, connections;
    private Button button3d;
    private boolean updatingSelection, pathModeActive;

    @Override public void start(Stage stage) { start(stage, getParameters().getRaw(), getParameters().getNamed()); }
    void start(Stage stage, java.util.List<String> raw, java.util.Map<String, String> named) {
        start(stage, raw, named, listener -> OperationalController.create(Platform::runLater, listener));
    }
    // Test seam for deterministic worker/UI barriers; production uses the bounded default controller above.
    void start(Stage stage, java.util.List<String> raw, java.util.Map<String, String> named,
               java.util.function.Function<java.util.function.Consumer<OperationalSnapshot>, OperationalController> factory) {
        boolean supports3d = Platform.isSupported(ConditionalFeature.SCENE3D);
        world = new TspWorldView(supports3d, raw.contains("--2d")); world.setId("tsp-world");
        world.setOnSelection(this::selected);
        button3d = new Button("3D"); button3d.setId("mode-3d"); button3d.setDisable(!supports3d); button3d.setOnAction(e -> world.setThreeD(true));
        world.setOnMode(text -> { mode.setText(text); button3d.setText(world.isThreeD() ? "3D ATIVO" : "3D"); });
        var button2d = new Button("MAPA 2D"); button2d.setOnAction(e -> world.setThreeD(false));
        var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(12, new VBox(3, label("NODEWALKER", "app-title"), label("ÚLTIMOS REFÚGIOS / LABORATÓRIO DE ALGORITMOS", "eyebrow")), spacer, button3d, button2d);
        header.setAlignment(Pos.CENTER_LEFT); header.setPadding(new Insets(0, 0, 17, 0));
        mapTitle.getStyleClass().add("map-title"); mapSubtitle.getStyleClass().add("map-subtitle"); mapSubtitle.setWrapText(true);
        var heading = new VBox(5, mapTitle, mapSubtitle); heading.setMouseTransparent(true); heading.setMaxWidth(550);
        StackPane.setAlignment(heading, Pos.TOP_LEFT); StackPane.setMargin(heading, new Insets(23));
        var legend = label("BASE 0 · [O] ORIGEM · [D] DESTINO · CUSTOS DA MATRIZ", "map-legend");
        legend.setMouseTransparent(true); StackPane.setAlignment(legend, Pos.BOTTOM_LEFT); StackPane.setMargin(legend, new Insets(22));
        var mapCard = new StackPane(world, heading, legend); mapCard.getStyleClass().add("map-card");
        var playbackText = label("", "playback-caption"); playbackText.setWrapText(true); playbackText.setId("playback-caption");
        var pause = new Button("Pausar"); pause.setId("playback-pause"); pause.setOnAction(e -> world.pausePlayback());
        var replay = new Button("Repetir desenho"); replay.setId("playback-replay"); replay.setOnAction(e -> world.replayPath());
        var finish = new Button("Mostrar tudo"); finish.setId("playback-finish"); finish.setOnAction(e -> world.finishPlayback());
        var speed = new ComboBox<Double>(); speed.setId("playback-speed"); speed.getItems().setAll(.25, .5, 1.0, 2.0, 4.0); speed.setValue(1.0);
        speed.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Double value) { return value == null ? "" : new java.text.DecimalFormat("0.##").format(value) + "×"; }
            public Double fromString(String value) { throw new UnsupportedOperationException(); }
        });
        speed.setOnAction(e -> world.setPlaybackSpeed(speed.getValue())); speed.setTooltip(new Tooltip("Velocidade da animação. Não altera o algoritmo ou o tempo do cálculo."));
        var playbackControls = new FlowPane(6, 6, pause, replay, finish, speed); playbackControls.setAlignment(Pos.CENTER);
        var playbackBar = new VBox(6, playbackText, playbackControls); playbackBar.setId("playback-bar"); playbackBar.setAlignment(Pos.CENTER);
        playbackBar.getStyleClass().add("playback-bar"); playbackBar.setMaxSize(540, Region.USE_PREF_SIZE); playbackBar.setVisible(false); playbackBar.setManaged(false);
        StackPane.setAlignment(playbackBar, Pos.BOTTOM_CENTER); StackPane.setMargin(playbackBar, new Insets(0, 12, 43, 12)); mapCard.getChildren().add(playbackBar);
        world.setOnPlayback(text -> {
            playbackText.setText(text); pause.setText(world.isPlaybackPaused() ? "Continuar" : "Pausar");
            pause.setDisable(!world.isAnimating()); finish.setDisable(!world.isAnimating()); replay.setDisable(!world.hasPlaybackPath());
            boolean visible = !text.isEmpty() && (world.isAnimating() || world.hasPlaybackPath());
            playbackBar.setVisible(visible); playbackBar.setManaged(visible);
        });
        refuges.setId("refuge-selection"); refuges.setMaxWidth(Double.MAX_VALUE);
        refuges.setOnAction(e -> { if (!updatingSelection && refuges.getSelectionModel().getSelectedIndex() >= 0) world.select(refuges.getSelectionModel().getSelectedIndex()); });
        coordinates.setWrapText(true); coordinates.getStyleClass().add("coordinates");
        var focus = new Button("Enquadrar refúgio [F]"); focus.setOnAction(e -> world.focusSelection());
        var inspection = new VBox(9, refuges, coordinates, focus);
        decoration = new CheckBox("Vegetação, rochas e ruínas [D]"); decoration.setSelected(!raw.contains("--no-decoration")); decoration.setOnAction(e -> world.setDecoration(decoration.isSelected()));
        simplified = new CheckBox("Geometria simplificada [S]"); simplified.setSelected(raw.contains("--simplified")); simplified.setOnAction(e -> world.setSimplified(simplified.isSelected()));
        connections = new CheckBox("Conexões disponíveis (filtro visual)"); connections.setSelected(false); connections.setOnAction(e -> { world.setConnections(connections.isSelected()); updateMapSubtitle(); });
        var reduced = new CheckBox("Rótulos reduzidos"); reduced.setOnAction(e -> world.setReducedLabels(reduced.isSelected()));
        var reset = new Button("Reset [R]"); reset.setOnAction(e -> world.resetCamera());
        var top = new Button("Superior [T]"); top.setOnAction(e -> world.topView());
        mode.setWrapText(true); mode.getStyleClass().add("muted-small");
        var weights = new ComboBox<EdgeWeightMode>(); weights.setId("edge-weight-mode"); weights.getItems().setAll(EdgeWeightMode.values()); weights.setValue(EdgeWeightMode.NONE); weights.setMaxWidth(Double.MAX_VALUE);
        var scope = new ComboBox<EdgeWeightPolicy.Scope>(); scope.setId("edge-weight-scope"); scope.getItems().setAll(EdgeWeightPolicy.Scope.values()); scope.setValue(EdgeWeightPolicy.Scope.AVAILABLE); scope.setMaxWidth(Double.MAX_VALUE);
        scope.setVisible(false); scope.setManaged(false);
        weights.setOnAction(e -> {
            boolean all=weights.getValue()==EdgeWeightMode.CONNECTIONS;
            if(all)connections.setSelected(true);
            scope.setVisible(all);scope.setManaged(all);world.setWeightMode(weights.getValue());updateMapSubtitle();
        });
        scope.setOnAction(e -> {world.setWeightScope(scope.getValue());updateMapSubtitle();});
        var exploration = new VBox(9, new HBox(8, reset, top), decoration, simplified, connections, label("Pesos reais das arestas", "muted-small"), weights, scope, reduced, mode);
        panel = new OperationalPanel(inspection, exploration);
        controller = factory.apply(this::accept); panel.bind(controller); panel.accept(controller.snapshot());
        var split = new SplitPane(mapCard, panel); split.setDividerPositions(.75); SplitPane.setResizableWithParent(panel, false);
        var root = new BorderPane(split, header, null, label("ARRASTE / ORBITAR · RODA / ZOOM · CLIQUE / INSPECIONAR · M / 3D–2D", "footer"), null); root.setPadding(new Insets(18));
        scene = new Scene(root, 1380, 870); scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        scene.setOnKeyPressed(e -> {
            if (editingControl(scene.getFocusOwner())) return;
            KeyCode key = e.getCode();
            if (key == KeyCode.R) world.resetCamera(); else if (key == KeyCode.T) world.topView(); else if (key == KeyCode.F) world.focusSelection();
            else if (key == KeyCode.M) world.setThreeD(!world.isThreeD()); else if (key == KeyCode.D) decoration.fire(); else if (key == KeyCode.S) simplified.fire(); else return;
            e.consume();
        });
        world.setDecoration(decoration.isSelected()); world.setSimplified(simplified.isSelected()); world.setConnections(connections.isSelected());
        stage.setScene(scene); stage.setTitle("NodeWalker | Laboratório interativo"); stage.setMinWidth(960); stage.setMinHeight(680); stage.show();
        if (named.containsKey("demo")) panel.load(DemoCatalog.forSize(Integer.parseInt(named.get("demo"))));
    }
    private static boolean editingControl(Node node) {
        for (Node at = node; at != null; at = at.getParent())
            if (at instanceof TextInputControl || at instanceof ComboBoxBase<?> || at instanceof Spinner<?> || at instanceof ListView<?> || at instanceof TableView<?> || at instanceof ButtonBase) return true;
        return false;
    }
    OperationalController controllerForReview() { return controller; }
    private void accept(OperationalSnapshot next) {
        panel.accept(next);
        if (next.problem() == null) {
            model = null; refuges.getItems().clear();
            world.clear(switch (next.phase()) {
                case CANCELADO -> "Preparação cancelada. Gere uma instância para continuar.";
                case ERRO -> "Falha ao preparar a instância. Consulte o diagnóstico no painel.";
                case SEM_INSTANCIA -> "Configure N e seed; clique GERAR.";
                default -> "Gerando instância em background…";
            });
            return;
        }
        if (next.scene() != null && next.scene() != model) show(next.scene());
        if (next.scene() instanceof PendingSceneModel)
            mapSubtitle.setText((next.phase() == OperationalSnapshot.Phase.CANCELADO ? "PREPARAÇÃO CANCELADA" :
                    next.phase() == OperationalSnapshot.Phase.ERRO ? "PREPARAÇÃO INTERROMPIDA" : "APRESENTAÇÃO PENDENTE")
                    + " · SEM RESPOSTA ATIVA");
    }
    private void show(WorldSceneModel prepared) {
        boolean newInstance = model == null || model.problem() != prepared.problem();
        boolean pathMode = prepared instanceof ShortestPathSceneModel;
        if (!(prepared instanceof PendingSceneModel) && pathMode != pathModeActive) {
            pathModeActive = pathMode; connections.setSelected(pathMode); world.setConnections(pathMode);
        }
        model = prepared;
        if (newInstance) {
            updatingSelection = true;
            refuges.getItems().setAll(prepared.refuges().stream().map(r -> r.index() == 0 ? "00 / BASE" : String.format("%02d / REFÚGIO", r.index())).toList());
            updatingSelection = false;
        }
        mapTitle.setText("SETOR " + String.format("%02d", prepared.problem().size()) + " / ÚLTIMOS REFÚGIOS");
        world.setModel(prepared); updateMapSubtitle();
    }
    private void updateMapSubtitle() {
        if(model instanceof PendingSceneModel) mapSubtitle.setText("APRESENTAÇÃO PENDENTE · SEM RESPOSTA ATIVA");
        else if(model instanceof ShortestPathSceneModel paths) mapSubtitle.setText("CAMINHOS MÍNIMOS · "+(switch(paths.graph().topology().kind()){case COMPLETE -> "COMPLETA";case SPARSE_CONNECTED -> "ESPARSA";case MANUAL -> "MANUAL";})
                +" · grafo: "+paths.graph().topology().edgeCount()+" · camada: "+world.visualConnectionCount()+" (filtro visual)");
        else if(model!=null) mapSubtitle.setText("TSP · CICLO HAMILTONIANO · ORIGEM 0");
    }
    // Explicit presentation fixture adapter retained for the opt-in graphical regression runner.
    void present(TspSceneModel prepared) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Present on FX Application Thread");
        panel.presentFixture(prepared); show(prepared); world.finishPlayback();
    }
    private void selected(int index) {
        if (model == null) return;
        var city = model.refuges().get(index).logical();
        String extra = model instanceof ShortestPathSceneModel paths ? paths.result().map(r -> "\n" + r.vertexState(index) + " · " + r.distanceTo(index).map(Object::toString).orElse("não informado")).orElse("") : "";
        updateMapSubtitle();
        coordinates.setText("ÍNDICE " + index + (index == 0 ? " · BASE" : "") + "\nX " + city.x() + "\nY " + city.y() + " (altitude)\nZ " + city.z() + extra);
        updatingSelection = true; refuges.getSelectionModel().select(index); updatingSelection = false;
    }
    private static Label label(String text, String style) { var label = new Label(text); label.getStyleClass().add(style); return label; }
    @Override public void stop() { if (controller != null) controller.close(); if (scene != null) scene.setOnKeyPressed(null); if (world != null) world.close(); }
    public static void main(String[] args) { launch(args); }
}
