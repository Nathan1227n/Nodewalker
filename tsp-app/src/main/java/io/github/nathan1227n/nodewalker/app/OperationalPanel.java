package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.ShortestPathSceneModel;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathExecutionOptions;
import io.github.nathan1227n.nodewalker.core.solver.SolverExecutionOptions;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Objects;

/** Essential controls first; all diagnostics/settings remain available in one optional area. */
final class OperationalPanel extends VBox {
    private final TextField count = field("5", "city-count"), seed = field("23", "instance-seed");
    private final TextField nodes = field("1000000", "node-limit"), millis = field("2000", "time-limit");
    private final TextField vertices = field("10000", "vertex-limit"), entries = field("1000000", "entry-limit");
    private final TextField topologySeed = field("23", "topology-seed"), extras = field("0", "topology-extras");
    private final ComboBox<ExecutionRequest.Algorithm> algorithm = new ComboBox<>();
    private final ComboBox<Integer> origin = new ComboBox<>(), destination = new ComboBox<>();
    private final ComboBox<RefugeTopology.Kind> topology = new ComboBox<>();
    private final ComboBox<DemoCatalog> presets = new ComboBox<>();
    private final CheckBox acknowledge = new CheckBox("Executar TSP grande com limites de segurança");
    private final Label phase = label("Gere seus refúgios para começar"), identity = label(""), error = label("");
    private final Label status = label(""), kind = label(""), certification = label(""), cost = label(""), route = label("");
    private final Label internalTime = label(""), summary = label(""), identifiers = label("");
    private final TextArea preflight = area(5), metrics = area(6), allDistances = area(5);
    private final Button present = new Button("Apresentar resultado"), compare = new Button("Comparar TSP");
    private final Label comparisonSummary = label("");
    private ResultsWindow resultsWindow;
    private final Button generate = new Button("Gerar"), execute = new Button("EXECUTAR"), cancel = new Button("Cancelar busca");
    private final Button apply = new Button("Aplicar ajustes");
    private final VBox pathConfiguration, pathAdvanced, tspAdvanced, resultBox, reportActions;
    private final TitledPane details;
    private OperationalController controller;
    private OperationalSnapshot snapshot;
    private boolean updating, pendingExecute;
    private long listedInstance = -1, actionRevision;

    OperationalPanel(Node inspection, Node exploration) {
        setSpacing(12); setPadding(new Insets(16)); setPrefWidth(335); setMinWidth(300); getStyleClass().add("control-panel");
        count.setAccessibleText("Número de refúgios"); seed.setAccessibleText("Seed da instância");
        count.setPrefColumnCount(3); seed.setPrefColumnCount(8);
        var nColumn = column("Refúgios", count); nColumn.setPrefWidth(62);
        var seedColumn = column("Seed", seed); HBox.setHgrow(seedColumn, Priority.ALWAYS);
        var instanceRow = new HBox(8, nColumn, seedColumn, generate); instanceRow.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        presets.setId("demo-catalog"); presets.getItems().setAll(DemoCatalog.values()); presets.setPromptText("Exemplos"); presets.setPrefWidth(115); presets.setMaxWidth(115);
        presets.setButtonCell(new ListCell<>() { @Override protected void updateItem(DemoCatalog value, boolean empty) { super.updateItem(value, empty); setText("Exemplos"); } });
        algorithm.setId("algorithm"); algorithm.getItems().setAll(ExecutionRequest.Algorithm.values()); algorithm.setValue(ExecutionRequest.Algorithm.BACKTRACKING); algorithm.setMaxWidth(Double.MAX_VALUE);
        origin.setId("origin"); destination.setId("destination"); topology.setId("topology");
        origin.setMaxWidth(Double.MAX_VALUE); destination.setMaxWidth(Double.MAX_VALUE);
        topology.getItems().setAll(RefugeTopology.Kind.COMPLETE, RefugeTopology.Kind.SPARSE_CONNECTED); topology.setValue(RefugeTopology.Kind.SPARSE_CONNECTED); topology.setMaxWidth(Double.MAX_VALUE);
        topology.setConverter(new javafx.util.StringConverter<>() {
            public String toString(RefugeTopology.Kind value) { return value == null ? "" : ShortestPathSceneModel.topologyLabel(value); }
            public RefugeTopology.Kind fromString(String text) { throw new UnsupportedOperationException(); }
        });
        var from = column("Origem", origin); var to = column("Destino", destination);
        HBox.setHgrow(from, Priority.ALWAYS); HBox.setHgrow(to, Priority.ALWAYS);
        pathConfiguration = new VBox(8, new HBox(8, from, to), topology);
        pathAdvanced = new VBox(8, row("Seed conexões", topologySeed), row("Extras (0…N−2)", extras), row("Limite vértices", vertices), row("Limite entradas", entries));
        tspAdvanced = new VBox(8, row("Limite nós TSP", nodes)); updatePathControls();
        acknowledge.setId("acknowledge-expensive"); acknowledge.setWrapText(true); show(acknowledge, false);
        preflight.setId("preflight-details");
        var limits = new VBox(8, row("Tempo máx. (ms)", millis), tspAdvanced, pathAdvanced, apply, preflight, identifiers);
        var data = new VBox(8, metrics, allDistances);
        var tabs = new TabPane(tab("Ajustes", limits), tab("Dados", data), tab("Mapa", exploration), tab("Refúgio", inspection));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setPrefHeight(330); tabs.setMinWidth(0);
        details = new TitledPane("Detalhes e ajustes", tabs); details.setId("operational-details"); details.setExpanded(false); details.setAnimated(false);
        status.setId("solver-status"); kind.setId("route-kind"); certification.setId("certification"); cost.setId("result-cost"); route.setId("result-route");
        status.getStyleClass().add("muted-small"); certification.getStyleClass().add("certification"); certification.setWrapText(false);
        cost.getStyleClass().add("cost-value"); cost.setStyle("-fx-font-size: 28px;"); route.getStyleClass().add("route-sequence"); internalTime.getStyleClass().add("muted-small");
        internalTime.setTooltip(new Tooltip("Tempo interno do solver. Não inclui geração, desenho ou reprodução animada."));
        var routeScroll = new ScrollPane(route); routeScroll.setFitToWidth(true); routeScroll.setMaxHeight(76); routeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); routeScroll.getStyleClass().add("route-scroll");
        var copy = new Button("Copiar"); copy.setAccessibleText("Copiar resultado e métricas"); copy.setTooltip(new Tooltip("Copiar resultado e métricas")); copy.getStyleClass().add("quiet-button"); copy.setOnAction(e -> {
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(status.getText() + "\n" + cost.getText() + "\n" + route.getText() + "\n" + metrics.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
        });
        HBox.setHgrow(cost, Priority.ALWAYS);
        var costRow = new HBox(8, cost, copy); costRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        resultBox = new VBox(7, new Separator(), kind, costRow, certification, internalTime, routeScroll, status); resultBox.getStyleClass().add("compact-result"); show(resultBox, false);
        generate.setId("generate"); execute.setId("execute"); cancel.setId("cancel");
        execute.getStyleClass().add("primary-action"); execute.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(execute, Priority.ALWAYS);
        var actions = new HBox(7, execute, cancel); show(cancel, false);
        phase.getStyleClass().add("status-label"); identity.getStyleClass().add("muted-small"); summary.getStyleClass().add("muted-small"); error.getStyleClass().add("reason");
        error.visibleProperty().bind(error.textProperty().isNotEmpty()); error.managedProperty().bind(error.visibleProperty());
        HBox.setHgrow(phase, Priority.ALWAYS);
        var heading = new HBox(8, phase, presets); heading.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        present.setId("present-results"); compare.setId("compare-results");
        present.setMaxWidth(Double.MAX_VALUE); compare.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(present, Priority.ALWAYS); HBox.setHgrow(compare, Priority.ALWAYS);
        present.setOnAction(e -> openResults(false)); compare.setOnAction(e -> openResults(true));
        comparisonSummary.getStyleClass().add("muted-small"); comparisonSummary.setId("comparison-summary");
        reportActions = new VBox(8, new HBox(8, present, compare), comparisonSummary);
        reportActions.setId("report-actions"); show(reportActions, false);
        var content = new VBox(10, heading, identity, instanceRow, new Separator(), column("Algoritmo", algorithm), pathConfiguration,
                summary, acknowledge, actions, error, resultBox, reportActions);
        content.setMinHeight(Region.USE_PREF_SIZE);
        var scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.getStyleClass().add("simple-controls-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS); getChildren().addAll(scroll, details);
        presets.setOnAction(e -> {
            if (updating || presets.getValue() == null) return;
            var preset = presets.getValue(); count.setText("" + preset.cityCount()); seed.setText("" + preset.seed()); nodes.setText("" + preset.nodeBudget()); generate();
        });
        generate.setOnAction(e -> generate()); execute.setOnAction(e -> execute());
        cancel.setOnAction(e -> { actionRevision++; pendingExecute = false; controller.cancel(); }); apply.setOnAction(e -> apply());
        algorithm.setOnAction(e -> { updatePathControls(); apply(); }); topology.setOnAction(e -> apply()); origin.setOnAction(e -> apply());
        destination.setOnAction(e -> { if (!updating && destination.getValue() != null) safe(() -> controller.destination(destination.getValue())); });
        for (var field : new TextField[]{nodes, millis, vertices, entries, topologySeed, extras}) field.setOnAction(e -> apply());
        execute.setDisable(true); cancel.setDisable(true);
    }
    private void openResults(boolean comparison) {
        if (snapshot == null || getScene() == null) return;
        if (resultsWindow == null) resultsWindow = new ResultsWindow(getScene().getWindow(), value -> {
            if (snapshot == null || snapshot.phase().busy()) return;
            algorithm.setValue(value); execute();
        }, () -> { actionRevision++; pendingExecute = false; controller.cancel(); });
        resultsWindow.show(snapshot, comparison);
    }
    void bind(OperationalController value) { controller = Objects.requireNonNull(value); }
    void load(DemoCatalog demo) { presets.setValue(demo); }
    private void updatePathControls() {
        boolean path = algorithm.getValue() == ExecutionRequest.Algorithm.DIJKSTRA;
        show(pathConfiguration, path); show(pathAdvanced, path); show(tspAdvanced, !path);
    }
    private ExecutionRequest request() {
        return new ExecutionRequest(algorithm.getValue(), origin.getValue() == null ? 0 : origin.getValue(), topology.getValue(),
                Long.parseLong(topologySeed.getText().trim()), Integer.parseInt(extras.getText().trim()),
                SolverExecutionOptions.limits(Long.parseLong(nodes.getText().trim()), Long.parseLong(millis.getText().trim())),
                ShortestPathExecutionOptions.limits(Long.parseLong(vertices.getText().trim()), Long.parseLong(entries.getText().trim()), Long.parseLong(millis.getText().trim())));
    }
    private void safe(Runnable action) {
        if (updating || controller == null) return;
        try { error.setText(""); action.run(); }
        catch (RuntimeException failure) { pendingExecute = false; error.setText(failure.getMessage()); }
    }
    private void generate() { actionRevision++; safe(() -> { pendingExecute = false; controller.generate(Integer.parseInt(count.getText().trim()), Long.parseLong(seed.getText().trim())); }); }
    private void apply() { if (updating) return; actionRevision++; safe(() -> { pendingExecute = false; controller.configure(request()); }); }
    private void execute() { actionRevision++; safe(() -> {
        if (snapshot == null || snapshot.problem() == null) throw new IllegalStateException("Gere os refúgios primeiro.");
        var configured = request();
        if (configured.algorithm().tsp() && snapshot.n() > configured.tspOptions().policy().recommendedMaxCities(configured.algorithm().coreAlgorithm()) && !acknowledge.isSelected())
            throw new IllegalArgumentException("Esta busca pode ser longa. Confirme a execução limitada abaixo do algoritmo.");
        if (!configured.equals(snapshot.request())) { pendingExecute = true; controller.configure(configured); }
        else if (snapshot.phase() == OperationalSnapshot.Phase.VALIDANDO_EXECUCAO) pendingExecute = true;
        else controller.execute();
    }); }
    void accept(OperationalSnapshot next) {
        snapshot = next;
        if (next.phase() == OperationalSnapshot.Phase.ERRO || next.phase() == OperationalSnapshot.Phase.CANCELADO) pendingExecute = false;
        updating = true;
        try {
            phase.setText(switch (next.phase()) {
                case SEM_INSTANCIA -> "Crie uma instância";
                case GERANDO_INSTANCIA -> "Preparando refúgios…";
                case INSTANCIA_PRONTA -> "Pronto para executar";
                case VALIDANDO_EXECUCAO -> "Preparando execução…";
                case EXECUTANDO_ALGORITMO -> "Calculando…";
                case CANCELAMENTO_SOLICITADO -> "Cancelando…";
                case CONCLUIDO -> "Cálculo concluído";
                case CANCELADO -> next.result() == null ? "Preparação cancelada" : "Busca cancelada";
                case LIMITE_ATINGIDO -> "Limite de busca atingido";
                case ERRO -> "Não foi possível concluir";
            });
            identity.setText(next.problem() == null ? "N e seed definem uma instância reproduzível." : next.n() + " refúgios · seed " + next.seed());
            identifiers.setText("Instância #" + next.instanceId() + " · execução #" + next.runId());
            boolean busy = next.phase().busy();
            for (var field : new TextField[]{nodes, millis, vertices, entries, topologySeed, extras}) field.setDisable(busy);
            destination.setDisable(next.problem() == null); generate.setDisable(busy); presets.setDisable(busy); algorithm.setDisable(busy); origin.setDisable(busy); topology.setDisable(busy); apply.setDisable(busy);
            execute.setDisable(busy || next.problem() == null); execute.setText(next.history().isEmpty() ? "EXECUTAR" : "EXECUTAR NOVAMENTE");
            show(cancel, busy); cancel.setDisable(next.phase() == OperationalSnapshot.Phase.CANCELAMENTO_SOLICITADO);
            if (next.problem() != null && listedInstance != next.instanceId()) {
                listedInstance = next.instanceId(); origin.getItems().clear(); destination.getItems().clear();
                for (int i = 0; i < next.n(); i++) { origin.getItems().add(i); destination.getItems().add(i); }
                origin.setValue(next.request().origin()); destination.setValue(next.destination());
            }
            boolean outside = next.problem() != null && next.request().algorithm().tsp()
                    && next.n() > next.request().tspOptions().policy().recommendedMaxCities(next.request().algorithm().coreAlgorithm());
            show(acknowledge, outside);
            summary.setText(outside ? "TSP grande: a busca pode terminar antes de provar o ótimo."
                    : "Tempo máximo: " + (next.request().algorithm().tsp() ? HudPresentation.optional(next.request().tspOptions().maxElapsedMillis()) : next.request().pathOptions().maxElapsedMillis()) + " ms · Detalhes para ajustar");
            if (!next.diagnostic().isEmpty()) error.setText(next.diagnostic());
            preflight.setText(next.preflight());
            var hud = HudPresentation.of(next);
            show(resultBox, next.result() != null);
            status.setText(hud.status()); kind.setText(hud.headline()); certification.setText(hud.certification()); cost.setText(hud.cost()); route.setText(hud.route());
            java.util.OptionalLong measured = next.result() instanceof ExecutionRecord.Tsp t ? t.result().metrics().elapsedNanos()
                    : next.result() instanceof ExecutionRecord.Paths p ? p.result().metrics().elapsedNanos() : java.util.OptionalLong.empty();
            internalTime.setText("Cálculo: " + HudPresentation.elapsed(measured));
            metrics.setText(hud.metrics() + "\n" + hud.reason() + (next.diagnostic().isEmpty() ? "" : "\n" + next.diagnostic()));
            present.setDisable(next.result() == null); compare.setDisable(next.problem() == null);
            show(reportActions, !next.history().isEmpty());
            var comparison = TspComparison.of(next.instanceId(), next.history());
            comparisonSummary.setText("Histórico TSP: " + comparison.headline());
            show(comparisonSummary, next.history().stream().anyMatch(ExecutionRecord.Tsp.class::isInstance));
            if (resultsWindow != null) resultsWindow.update(next);
            show(allDistances, next.result() instanceof ExecutionRecord.Paths);
            if (next.result() instanceof ExecutionRecord.Paths paths) {
                var text = new StringBuilder();
                for (int i = 0; i < next.n(); i++) text.append(i).append(" · ").append(paths.result().vertexState(i)).append(" · ")
                        .append(paths.result().distanceTo(i).map(Object::toString).orElse("não informado")).append('\n');
                allDistances.setText(text.toString());
            }
        } finally { updating = false; }
        if (pendingExecute && next.phase() == OperationalSnapshot.Phase.INSTANCIA_PRONTA) {
            pendingExecute = false; long expectedAction = actionRevision;
            Platform.runLater(() -> {
                var active = controller.snapshot();
                if (expectedAction == actionRevision && active.instanceId() == next.instanceId() && active.runId() == next.runId()
                        && active.request().equals(next.request()) && !active.phase().busy()) safe(() -> controller.execute());
            });
        }
    }
    void presentFixture(io.github.nathan1227n.nodewalker.app.world.TspSceneModel prepared) {
        show(resultBox, true); var p = prepared.presentation();
        status.setText(p.status()); kind.setText(p.headline()); certification.setText(p.certification());
        internalTime.setText("Cálculo: " + HudPresentation.elapsed(prepared.metrics().elapsedNanos()));
        cost.setText(HudPresentation.optional(prepared.cost())); route.setText(HudPresentation.sequence(prepared.routeIndices())); metrics.setText(HudPresentation.tspMetrics(prepared.metrics()));
    }
    private static void show(Node node, boolean value) { node.setVisible(value); node.setManaged(value); }
    private static TextField field(String text, String id) { var field = new TextField(text); field.setId(id); field.setMinWidth(0); field.setPrefColumnCount(8); return field; }
    private static Label label(String text) { var label = new Label(text); label.setWrapText(true); label.setMaxWidth(Double.MAX_VALUE); label.setMinHeight(Region.USE_PREF_SIZE); return label; }
    private static TextArea area(int rows) { var area = new TextArea(); area.setEditable(false); area.setWrapText(true); area.setPrefRowCount(rows); area.setMinHeight(Region.USE_PREF_SIZE); return area; }
    private static VBox column(String text, Node control) { var label = label(text); label.getStyleClass().add("muted-small"); return new VBox(5, label, control); }
    private static HBox row(String text, Node control) { var label = label(text); label.setMinWidth(120); HBox.setHgrow(control, Priority.ALWAYS); return new HBox(8, label, control); }
    private static Tab tab(String title, Node content) {
        var scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return new Tab(title, scroll);
    }
}
