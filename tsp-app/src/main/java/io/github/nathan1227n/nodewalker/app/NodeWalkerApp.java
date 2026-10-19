package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.demo.DemoLoader;
import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;

import java.util.concurrent.Executors;

import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Real, bounded demo presentation. Operational configuration/execution HUD belongs to #12.
 */
public final class NodeWalkerApp extends Application {
    private TspWorldView world;
    private DemoLoader loader;
    private TspSceneModel model;
    private Scene scene;
    private ComboBox<DemoCatalog> demos;
    private ComboBox<String> refuges;
    private ChangeListener<Number> refugeListener;
    private ChangeListener<DemoCatalog> demoListener;
    private Label status, routeKind, certification, cost, route, coordinates, observed, reason, mode, mapTitle, mapSubtitle, limits;
    private CheckBox decoration;
    private CheckBox simplified;
    private CheckBox connections;
    private Button button3d;

    @Override
    public void start(Stage stage) {
        start(stage, getParameters().getRaw(), getParameters().getNamed());
    }

    // Same composition used by the opt-in graphical review launcher, with explicit launch parameters.
    void start(Stage stage, java.util.List<String> raw, java.util.Map<String, String> named) {
        int n = Integer.parseInt(named.getOrDefault("demo", "5"));
        DemoCatalog initialDemo = DemoCatalog.forSize(n);
        // Application's constructor is called by the launcher, not by the FX Application Thread.
        status = new Label("PREPARANDO INSTÂNCIA");
        routeKind = new Label();
        certification = new Label();
        cost = new Label("—");
        route = new Label();
        coordinates = new Label("Selecione um refúgio");
        observed = new Label();
        reason = new Label();
        mode = new Label();
        mapTitle = new Label();
        mapSubtitle = new Label();
        limits = new Label();
        boolean supports3d = Platform.isSupported(ConditionalFeature.SCENE3D);
        boolean force2d = raw.contains("--2d");
        world = new TspWorldView(supports3d, force2d);
        world.setId("tsp-world");
        world.setOnSelection(this::selected);
        world.setOnMode(text -> {
            mode.setText(text);
            button3d.setText(world.isThreeD() ? "3D ATIVO" : "3D");
        });
        loader = new DemoLoader(Executors.newSingleThreadExecutor(r -> {
            var thread = new Thread(r, "nodewalker-demo");
            thread.setDaemon(true);
            return thread;
        }), Platform::runLater);

        var title = label("NODEWALKER", "app-title");
        var subtitle = label("ÚLTIMOS REFÚGIOS  /  LABORATÓRIO TSP", "eyebrow");
        var brand = new VBox(3, title, subtitle);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        button3d = new Button("3D");
        button3d.setId("mode-3d");
        button3d.setDisable(!supports3d);
        button3d.setOnAction(e -> world.setThreeD(true));
        var button2d = new Button("MAPA 2D");
        button2d.setOnAction(e -> world.setThreeD(false));
        var tag = label("G3 / MAPA REAL", "phase-tag");
        var header = new HBox(12, brand, spacer, tag, button3d, button2d);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 17, 0));

        mapTitle.getStyleClass().add("map-title");
        mapSubtitle.getStyleClass().add("map-subtitle");
        var mapHeading = new VBox(5, mapTitle, mapSubtitle);
        mapHeading.setMouseTransparent(true);
        StackPane.setAlignment(mapHeading, Pos.TOP_LEFT);
        StackPane.setMargin(mapHeading, new Insets(23));
        var legend = label("● BASE 0   /   ● REFÚGIOS    ·    ARESTAS DIRETAS NO ESPAÇO XYZ", "map-legend");
        legend.setMouseTransparent(true);
        StackPane.setAlignment(legend, Pos.BOTTOM_LEFT);
        StackPane.setMargin(legend, new Insets(22));
        var mapCard = new StackPane(world, mapHeading, legend);
        mapCard.getStyleClass().add("map-card");
        var mapReset = new Button("↺ RESET");
        mapReset.setOnAction(e -> world.resetCamera());
        var mapTop = new Button("↓ SUPERIOR");
        mapTop.setOnAction(e -> world.topView());
        var cameraTools = new HBox(7, mapReset, mapTop);
        cameraTools.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(cameraTools, Pos.TOP_RIGHT);
        StackPane.setMargin(cameraTools, new Insets(20));
        mapCard.getChildren().add(cameraTools);

        demos = new ComboBox<>();
        demos.setId("demo-catalog");
        demos.getItems().setAll(DemoCatalog.values());
        demos.setMaxWidth(Double.MAX_VALUE);
        limits.setWrapText(true);
        limits.getStyleClass().add("muted-small");
        var catalog = new VBox(9, label("01 / INSTÂNCIA REPRODUZÍVEL", "section-title"), demos, limits);

        status.getStyleClass().add("status-label");
        status.setId("solver-status");
        certification.setId("certification");
        routeKind.setId("route-kind");
        certification.getStyleClass().add("certification");
        certification.setWrapText(false);
        routeKind.getStyleClass().add("route-kind");
        cost.getStyleClass().add("cost-value");
        route.getStyleClass().add("route-sequence");
        route.setWrapText(true);
        observed.getStyleClass().add("muted-small");
        observed.setWrapText(true);
        reason.getStyleClass().add("reason");
        reason.setWrapText(true);
        var result = new VBox(8, label("02 / CICLO HAMILTONIANO", "section-title"), status, routeKind, certification,
                cost, label("CUSTO / MATRIZ ORIGINAL", "muted-small"), route, observed, reason);

        refuges = new ComboBox<>();
        refuges.setId("refuge-selection");
        refuges.setMaxWidth(Double.MAX_VALUE);
        coordinates.getStyleClass().add("coordinates");
        coordinates.setWrapText(true);
        var focus = new Button("Enquadrar refúgio [F]");
        focus.setOnAction(e -> world.focusSelection());
        var selected = new VBox(9, label("03 / INSPEÇÃO LÓGICA", "section-title"), refuges, coordinates, focus);

        decoration = new CheckBox("Vegetação, rochas e ruínas [D]");
        decoration.setSelected(!raw.contains("--no-decoration"));
        decoration.setOnAction(e -> world.setDecoration(decoration.isSelected()));
        simplified = new CheckBox("Geometria simplificada [S]");
        simplified.setSelected(raw.contains("--simplified"));
        simplified.setOnAction(e -> world.setSimplified(simplified.isSelected()));
        var reducedLabels = new CheckBox("Rótulos reduzidos / exploração limpa");
        reducedLabels.setOnAction(e -> world.setReducedLabels(reducedLabels.isSelected()));
        connections = new CheckBox("Conexões do selecionado (até 32)");
        connections.setOnAction(e -> world.setConnections(connections.isSelected()));
        var reset = new Button("Reset [R]");
        reset.setOnAction(e -> world.resetCamera());
        var top = new Button("Superior [T]");
        top.setOnAction(e -> world.topView());
        mode.getStyleClass().add("muted-small");
        mode.setWrapText(true);
        var navigation = new VBox(10, label("04 / EXPLORAÇÃO", "section-title"), new HBox(8, reset, top), decoration, simplified, reducedLabels, connections, mode);
        var controls = new VBox(21, catalog, separator(), result, separator(), selected, separator(), navigation);
        controls.getStyleClass().add("control-panel");
        controls.setPadding(new Insets(21));
        var scroll = new ScrollPane(controls);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(310);
        scroll.setMinWidth(285);
        scroll.getStyleClass().add("controls-scroll");

        var footer = label("ARRASTE PARA ORBITAR  ·  RODA PARA ZOOM  ·  CLIQUE PARA INSPECIONAR  ·  M ALTERNA 3D/2D", "footer");
        footer.setPadding(new Insets(13, 0, 0, 0));
        var root = new BorderPane();
        root.setPadding(new Insets(22));
        root.setTop(header);
        root.setCenter(mapCard);
        root.setRight(scroll);
        root.setBottom(footer);
        BorderPane.setMargin(scroll, new Insets(0, 0, 0, 16));
        scene = new Scene(root, 1380, 870);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        scene.setOnKeyPressed(e -> {
            if (e.getTarget() instanceof TextInputControl || demos.isShowing() || refuges.isShowing()) return;
            KeyCode code = e.getCode();
            if (code == KeyCode.R) world.resetCamera();
            else if (code == KeyCode.T) world.topView();
            else if (code == KeyCode.F) world.focusSelection();
            else if (code == KeyCode.M) world.setThreeD(!world.isThreeD());
            else if (code == KeyCode.D) decoration.fire();
            else if (code == KeyCode.S) simplified.fire();
            else return;
            e.consume();
        });
        refugeListener = (observable, oldValue, next) -> {
            if (next.intValue() >= 0) world.select(next.intValue());
        };
        refuges.getSelectionModel().selectedIndexProperty().addListener(refugeListener);
        demoListener = (observable, oldValue, next) -> {
            if (next != null) load(next);
        };
        demos.valueProperty().addListener(demoListener);
        world.setDecoration(decoration.isSelected());
        world.setSimplified(simplified.isSelected());
        stage.setScene(scene);
        stage.setTitle("NodeWalker | Últimos refúgios — TSP real");
        stage.setMinWidth(960);
        stage.setMinHeight(680);
        stage.show();
        demos.setValue(initialDemo);
    }

    private void load(DemoCatalog demo) {
        model = null;
        refuges.getItems().clear();
        world.clear("Preparando mundo e busca limitada em background…");
        mapTitle.setText("SETOR " + String.format("%02d", demo.cityCount()) + " / ÚLTIMOS REFÚGIOS");
        mapSubtitle.setText("N=" + demo.cityCount() + "  ·  SEED " + demo.seed() + "  ·  ORIGEM 0");
        status.setText("PREPARANDO INSTÂNCIA");
        routeKind.setText("Nenhum resultado recebido");
        certification.setText("");
        cost.setText("—");
        route.setText("");
        coordinates.setText("Coordenadas ainda não disponíveis");
        observed.setText("");
        reason.setText("");
        var preflight = demo.preflight();
        limits.setText("Backtracking · até " + demo.nodeBudget() + " nós / " + DemoCatalog.SOLVER_MILLIS
                + " ms\n(N−1)! = " + preflight.theoreticalRoutes() + " · custo fatorial\nGeração: "
                + DemoCatalog.GENERATION_CANDIDATES + " candidatos / 16 MiB estimados");
        loader.load(demo, this::present, diagnostic -> {
            status.setText("FALHA NO CARREGAMENTO");
            routeKind.setText("Nenhum resultado de solver fabricado");
            certification.setText("");
            reason.setText(diagnostic);
            world.clear("Não foi possível preparar a demonstração.");
        });
    }

    // FX-thread presentation adapter, also exercised by the opt-in graphical regression runner.
    void present(TspSceneModel prepared) {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Present on FX Application Thread");
        model = prepared;
        refuges.getItems().setAll(prepared.refuges().stream().map(r -> r.index() == 0 ? "00 / BASE" : String.format("%02d / REFÚGIO", r.index())).toList());
        var presentation = prepared.presentation();
        status.setText(presentation.status());
        routeKind.setText(presentation.headline());
        certification.setText(presentation.certification());
        certification.setStyle("-fx-text-fill: " + (prepared.certified() ? "#8ff6ed" : "#ffbe64") + ";");
        cost.setText(prepared.cost().isPresent() ? Long.toString(prepared.cost().getAsLong()) : "não informado");
        cost.setStyle(prepared.cost().isEmpty() ? "-fx-font-size: 22px;"
                : Long.toString(prepared.cost().getAsLong()).length() > 15 ? "-fx-font-size: 18px;" : "");
        route.setText(prepared.routeIndices().isEmpty() ? "Sem incumbente: nenhuma rota desenhada."
                : String.join(" → ", prepared.routeIndices().stream().map(Object::toString).toList()));
        observed.setText("Nós observados: " + (prepared.metrics().visitedNodes().isPresent()
                ? prepared.metrics().visitedNodes().getAsLong() : "não informado") + "\nTempo interno: "
                + (prepared.metrics().elapsedNanos().isPresent() ? String.format(java.util.Locale.ROOT, "%.2f ms", prepared.metrics().elapsedNanos().getAsLong() / 1e6) : "não informado"));
        reason.setText(prepared.result().flatMap(r -> r.reason()).orElse("Arestas diretas; o relevo é apenas visual."));
        world.setModel(prepared);
    }

    private void selected(int index) {
        if (model == null) return;
        var city = model.refuges().get(index).logical();
        coordinates.setText("ÍNDICE " + index + (index == 0 ? " · BASE\n" : "\n") + "X  " + city.x() + "\nY  " + city.y() + "  (altitude)\nZ  " + city.z());
        if (refuges.getSelectionModel().getSelectedIndex() != index) refuges.getSelectionModel().select(index);
    }

    private static Label label(String text, String style) {
        var label = new Label(text);
        label.getStyleClass().add(style);
        return label;
    }

    private static Separator separator() {
        return new Separator();
    }

    @Override
    public void stop() {
        if (loader != null) loader.close();
        if (demos != null && demoListener != null) demos.valueProperty().removeListener(demoListener);
        if (refuges != null && refugeListener != null)
            refuges.getSelectionModel().selectedIndexProperty().removeListener(refugeListener);
        if (scene != null) scene.setOnKeyPressed(null);
        if (world != null) world.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
