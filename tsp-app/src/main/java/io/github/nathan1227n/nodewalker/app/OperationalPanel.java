package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathExecutionOptions;
import io.github.nathan1227n.nodewalker.core.solver.SolverExecutionOptions;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Objects;
import java.util.function.Consumer;

/** FX controls only: the controller owns instance/execution identities and all background work. */
final class OperationalPanel extends VBox {
    private final TextField count = field("5", "city-count"), seed = field("23", "instance-seed");
    private final TextField nodes = field("1000000", "node-limit"), millis = field("2000", "time-limit");
    private final TextField vertices = field("10000", "vertex-limit"), entries = field("1000000", "entry-limit");
    private final TextField topologySeed = field("23", "topology-seed"), extras = field("0", "topology-extras");
    private final ComboBox<ExecutionRequest.Algorithm> algorithm = new ComboBox<>();
    private final ComboBox<Integer> origin = new ComboBox<>(), destination = new ComboBox<>();
    private final ComboBox<RefugeTopology.Kind> topology = new ComboBox<>();
    private final CheckBox acknowledge = new CheckBox("Confirmo busca TSP fora da faixa, com limites");
    private final Label phase = label("SEM INSTÂNCIA"), identity = label("Nenhuma instância ativa"), error = label("");
    private final Label status = label("SEM RESULTADO"), kind = label(""), certification = label(""), cost = label("não informado"), route = label("");
    private final Label algorithmName = label(""), resultContext = label(""), internalTime = label("Tempo interno: não informado");
    private final TextArea preflight = area(5), metrics = area(6), history = area(7), allDistances = area(5);
    private final Button generate = new Button("GERAR"), execute = new Button("EXECUTAR"), cancel = new Button("CANCELAR"), rerun = new Button("REEXECUTAR");
    private final Button apply = new Button("ATUALIZAR PREFLIGHT");
    private final VBox pathConfiguration;
    private final ComboBox<DemoCatalog> presets = new ComboBox<>();
    private OperationalController controller;
    private OperationalSnapshot snapshot;
    private boolean updating, pendingExecute;
    private long listedInstance = -1, displayedRun = -1, actionRevision;
    private TitledPane instancePane, configurationPane, resultPane;

    OperationalPanel(Node inspection, Node exploration) {
        setSpacing(9); setPadding(new Insets(16)); setPrefWidth(365); setMinWidth(305); getStyleClass().add("control-panel");
        acknowledge.setId("acknowledge-expensive");
        count.setAccessibleText("Número de cidades N"); seed.setAccessibleText("Seed da instância");
        presets.setId("demo-catalog"); presets.getItems().setAll(DemoCatalog.values()); presets.setPromptText("Presets N=3 / 5 / 8 / 12"); presets.setMaxWidth(Double.MAX_VALUE);
        algorithm.setId("algorithm"); algorithm.getItems().setAll(ExecutionRequest.Algorithm.values()); algorithm.setValue(ExecutionRequest.Algorithm.BACKTRACKING); algorithm.setMaxWidth(Double.MAX_VALUE);
        origin.setId("origin"); destination.setId("destination"); topology.setId("topology");
        topology.getItems().setAll(RefugeTopology.Kind.COMPLETE, RefugeTopology.Kind.SPARSE_CONNECTED); topology.setValue(RefugeTopology.Kind.SPARSE_CONNECTED);
        topology.setMaxWidth(Double.MAX_VALUE);
        topology.setConverter(new javafx.util.StringConverter<>() {
            public String toString(RefugeTopology.Kind kind) { return kind == null ? "" : io.github.nathan1227n.nodewalker.app.world.ShortestPathSceneModel.topologyLabel(kind); }
            public RefugeTopology.Kind fromString(String text) { throw new UnsupportedOperationException(); }
        });
        pathConfiguration = new VBox(7, row("Origem [O]", origin), row("Destino [D]", destination), topology,
                row("Seed topologia", topologySeed), row("Extras (0…N−2)", extras), row("Limite vértices", vertices), row("Limite entradas", entries));
        pathConfiguration.setVisible(false); pathConfiguration.setManaged(false);
        var configuration = new VBox(8, algorithm, pathConfiguration, row("Limite nós TSP", nodes), row("Tempo máx. (ms)", millis), apply, acknowledge, preflight);
        var instance = new VBox(8, row("N", count), row("Seed", seed), presets,
                label("Geração: 100.000 candidatos / 16 MiB estimados. Alterar campos não altera a instância ativa."));
        certification.setWrapText(false);
        status.setId("solver-status"); kind.setId("route-kind"); certification.setId("certification"); cost.setId("result-cost"); route.setId("result-route");
        status.getStyleClass().add("status-label"); cost.getStyleClass().add("cost-value"); certification.getStyleClass().add("certification");
        cost.setStyle("-fx-font-size: 19px;");
        var copy = new Button("COPIAR RESULTADO"); copy.setOnAction(e -> {
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(status.getText() + "\n" + cost.getText() + "\n" + route.getText() + "\n" + metrics.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        var result = new VBox(7, algorithmName, status, kind, resultContext, certification, cost, internalTime, route, copy,
                pane("Métricas e motivo", metrics, false), pane("Distâncias Dijkstra", allDistances, false), pane("Histórico / comparação TSP", history, false));
        instancePane = pane("01 / INSTÂNCIA", instance, true);
        configurationPane = pane("02 / PROBLEMA E PREFLIGHT", configuration, true);
        resultPane = pane("03 / RESULTADOS", result, false);
        var sections = new VBox(10, instancePane, configurationPane, resultPane, pane("04 / INSPEÇÃO", inspection, false), pane("05 / EXPLORAÇÃO", exploration, false));
        sections.setMinHeight(Region.USE_PREF_SIZE);
        var scroll = new ScrollPane(sections); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("controls-scroll"); VBox.setVgrow(scroll, Priority.ALWAYS);
        generate.setId("generate"); execute.setId("execute"); cancel.setId("cancel"); rerun.setId("rerun");
        var actions = new GridPane(); actions.setHgap(6); actions.setVgap(6);
        for (int i = 0; i < 2; i++) { var column = new ColumnConstraints(); column.setPercentWidth(50); actions.getColumnConstraints().add(column); }
        actions.addRow(0, generate, execute); actions.addRow(1, cancel, rerun);
        for (var button : new Button[]{generate, execute, cancel, rerun}) button.setMaxWidth(Double.MAX_VALUE);
        phase.getStyleClass().add("status-label"); error.getStyleClass().add("reason"); identity.getStyleClass().add("muted-small");
        getChildren().addAll(phase, identity, actions, error, scroll);
        presets.setOnAction(e -> {
            if (updating || presets.getValue() == null) return;
            var preset = presets.getValue(); count.setText("" + preset.cityCount()); seed.setText("" + preset.seed()); nodes.setText("" + preset.nodeBudget());
            generate();
        });
        generate.setOnAction(e -> generate()); execute.setOnAction(e -> execute()); rerun.setOnAction(e -> execute()); cancel.setOnAction(e -> { actionRevision++; pendingExecute = false; controller.cancel(); });
        apply.setOnAction(e -> apply());
        algorithm.setOnAction(e -> { updatePathControls(); apply(); }); topology.setOnAction(e -> apply()); origin.setOnAction(e -> apply());
        destination.setOnAction(e -> { if (!updating && destination.getValue() != null) safe(() -> controller.destination(destination.getValue())); });
        for (var field : new TextField[]{nodes, millis, vertices, entries, topologySeed, extras}) field.setOnAction(e -> apply());
        execute.setDisable(true); rerun.setDisable(true); cancel.setDisable(true);
    }
    void bind(OperationalController controller) { this.controller = Objects.requireNonNull(controller); }
    void load(DemoCatalog demo) { presets.setValue(demo); }
    private void updatePathControls() { boolean path = algorithm.getValue() == ExecutionRequest.Algorithm.DIJKSTRA; pathConfiguration.setVisible(path); pathConfiguration.setManaged(path); }
    private ExecutionRequest request() {
        return new ExecutionRequest(algorithm.getValue(), origin.getValue() == null ? 0 : origin.getValue(), topology.getValue(),
                Long.parseLong(topologySeed.getText().trim()), Integer.parseInt(extras.getText().trim()),
                SolverExecutionOptions.limits(Long.parseLong(nodes.getText().trim()), Long.parseLong(millis.getText().trim())),
                ShortestPathExecutionOptions.limits(Long.parseLong(vertices.getText().trim()), Long.parseLong(entries.getText().trim()), Long.parseLong(millis.getText().trim())));
    }
    private void safe(Runnable action) { if (updating || controller == null) return; try { error.setText(""); action.run(); } catch (RuntimeException failure) { pendingExecute = false; error.setText(failure.getMessage()); } }
    private void generate() { actionRevision++; safe(() -> { pendingExecute = false; controller.generate(Integer.parseInt(count.getText().trim()), Long.parseLong(seed.getText().trim())); }); }
    private void apply() { if (updating) return; actionRevision++; safe(() -> { pendingExecute = false; controller.configure(request()); }); }
    private void execute() { actionRevision++; safe(() -> {
        if (snapshot == null || snapshot.problem() == null) throw new IllegalStateException("Gere uma instância primeiro");
        var configured = request();
        if (configured.algorithm().tsp() && snapshot.n() > configured.tspOptions().policy().recommendedMaxCities(configured.algorithm().coreAlgorithm()) && !acknowledge.isSelected())
            throw new IllegalArgumentException("Leia o preflight e confirme a busca limitada fora da faixa recomendada.");
        if (!configured.equals(snapshot.request())) { pendingExecute = true; controller.configure(configured); }
        else controller.execute();
    }); }
    void accept(OperationalSnapshot next) {
        snapshot = next; if (next.phase() == OperationalSnapshot.Phase.ERRO || next.phase() == OperationalSnapshot.Phase.CANCELADO) pendingExecute = false; updating = true;
        try {
            phase.setText(next.phase().label() + (next.stage().isEmpty() ? "" : " · " + next.stage()));
            identity.setText(next.problem() == null ? "Sem instância disponível · pedido #" + next.instanceId() : "Instância #" + next.instanceId() + " · N=" + next.n() + " · seed=" + next.seed() + "\nExecução #" + next.runId());
            boolean busy = next.phase().busy();
            for (var field : new TextField[]{nodes, millis, vertices, entries, topologySeed, extras}) field.setDisable(busy);
            destination.setDisable(next.problem() == null);
            generate.setDisable(busy); presets.setDisable(busy); algorithm.setDisable(busy); origin.setDisable(busy); topology.setDisable(busy); apply.setDisable(busy);
            execute.setDisable(busy || next.problem() == null); rerun.setDisable(busy || next.history().isEmpty()); cancel.setDisable(!busy || next.phase() == OperationalSnapshot.Phase.CANCELAMENTO_SOLICITADO);
            if (next.problem() != null && listedInstance != next.instanceId()) {
                listedInstance = next.instanceId(); instancePane.setExpanded(false); origin.getItems().clear(); destination.getItems().clear();
                for (int i = 0; i < next.n(); i++) { origin.getItems().add(i); destination.getItems().add(i); }
                origin.setValue(next.request().origin()); destination.setValue(next.destination());
            }
            if (next.phase() == OperationalSnapshot.Phase.EXECUTANDO_ALGORITMO && displayedRun != next.runId()) {
                displayedRun = next.runId(); configurationPane.setExpanded(false); resultPane.setExpanded(true);
            }
            if (next.result() != null) resultPane.setExpanded(true);
            if (!next.diagnostic().isEmpty()) error.setText(next.diagnostic());
            preflight.setText(next.preflight());
            algorithmName.setText(next.request().algorithm().toString());
            resultContext.setText(next.request().algorithm().tsp() ? "N=" + next.n() + " · origem 0"
                    : "Origem " + next.request().origin() + " · destino " + next.destination() + " · " + io.github.nathan1227n.nodewalker.app.world.ShortestPathSceneModel.topologyLabel(next.request().topology()));
            java.util.OptionalLong measured = next.result() instanceof ExecutionRecord.Tsp t ? t.result().metrics().elapsedNanos()
                    : next.result() instanceof ExecutionRecord.Paths p ? p.result().metrics().elapsedNanos() : java.util.OptionalLong.empty();
            internalTime.setText("Tempo interno: " + HudPresentation.elapsed(measured));
            var hud = HudPresentation.of(next);
            status.setText(hud.status()); kind.setText(hud.headline()); certification.setText(hud.certification()); cost.setText(hud.cost()); route.setText(hud.route());
            metrics.setText(hud.metrics() + "\n" + hud.reason() + (next.diagnostic().isEmpty() ? "" : "\n" + next.diagnostic()));
            history.setText(HudPresentation.history(next.history()));
            if (next.result() instanceof ExecutionRecord.Paths paths) {
                var text = new StringBuilder();
                for (int i = 0; i < next.n(); i++) text.append(i).append(" · ").append(paths.result().vertexState(i)).append(" · ")
                        .append(paths.result().distanceTo(i).map(Object::toString).orElse("não informado")).append('\n');
                allDistances.setText(text.toString());
            } else allDistances.setText("Resultados próprios do Dijkstra; não comparar com custos TSP.");
        } finally { updating = false; }
        if (pendingExecute && next.phase() == OperationalSnapshot.Phase.INSTANCIA_PRONTA) {
            pendingExecute = false;
            long expectedAction = actionRevision;
            Platform.runLater(() -> {
                var active = controller.snapshot();
                if (expectedAction == actionRevision && active.instanceId() == next.instanceId()
                        && active.runId() == next.runId() && active.request().equals(next.request()) && !active.phase().busy())
                    safe(() -> controller.execute());
            });
        }
    }
    void presentFixture(io.github.nathan1227n.nodewalker.app.world.TspSceneModel prepared) {
        resultPane.setExpanded(true);
        algorithmName.setText("TSP · FIXTURE DE APRESENTAÇÃO");
        internalTime.setText("Tempo interno: " + HudPresentation.elapsed(prepared.metrics().elapsedNanos()));
        var p = prepared.presentation();
        status.setText(p.status()); kind.setText(p.headline()); certification.setText(p.certification());
        cost.setText(HudPresentation.optional(prepared.cost())); route.setText(HudPresentation.sequence(prepared.routeIndices()));
        metrics.setText(HudPresentation.tspMetrics(prepared.metrics()));
    }
    private static TextField field(String text, String id) { var field = new TextField(text); field.setId(id); field.setPrefColumnCount(12); return field; }
    private static Label label(String text) { var label = new Label(text); label.setWrapText(true); label.setMaxWidth(Double.MAX_VALUE); label.setMinHeight(Region.USE_PREF_SIZE); return label; }
    private static TextArea area(int rows) { var area = new TextArea(); area.setEditable(false); area.setWrapText(true); area.setPrefRowCount(rows); area.setMinHeight(Region.USE_PREF_SIZE); return area; }
    private static HBox row(String text, Node control) { var label = label(text); label.setMinWidth(120); HBox.setHgrow(control, Priority.ALWAYS); return new HBox(8, label, control); }
    private static TitledPane pane(String text, Node content, boolean expanded) { var pane = new TitledPane(text, content); pane.setExpanded(expanded); pane.setAnimated(false); return pane; }
}
