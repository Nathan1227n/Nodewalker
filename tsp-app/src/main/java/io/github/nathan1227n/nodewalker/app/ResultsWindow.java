package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.operation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.util.Optional;
import java.util.function.Consumer;

/** Opt-in presentation surface. Reads immutable snapshots; never runs a solver on the FX thread. */
final class ResultsWindow {
    private final Stage stage = new Stage();
    private final VBox result = new VBox(18), comparison = new VBox(18);
    private final TabPane tabs = new TabPane();
    private final Label context = text("", "report-context"), notice = text("", "report-note");
    private final Consumer<ExecutionRequest.Algorithm> execute;
    private OperationalSnapshot snapshot;
    private final Button cancel = new Button("Cancelar busca");

    ResultsWindow(Window owner, Consumer<ExecutionRequest.Algorithm> execute, Runnable cancelExecution) {
        this.execute = execute;
        stage.initOwner(owner); stage.setTitle("NodeWalker | Resultados e comparação");
        stage.setWidth(1000); stage.setHeight(820); stage.setMinWidth(700); stage.setMinHeight(500);
        var heading = text("Resultados do laboratório", "report-title");
        var copy = new Button("Copiar resumo"); copy.setId("copy-report"); copy.setOnAction(e -> {
            if (snapshot == null) return;
            var content = new ClipboardContent();
            content.putString(tabs.getSelectionModel().getSelectedIndex() == 0 ? ResultReport.of(snapshot).copyText()
                    : context.getText() + "\n\n" + TspComparison.of(snapshot.instanceId(), snapshot.history()).copyText());
            Clipboard.getSystemClipboard().setContent(content); notice.setText("Resumo copiado com instância e métricas reais. Tempos desta execução não constituem benchmark.");
        });
        cancel.setId("report-cancel"); cancel.setOnAction(e -> cancelExecution.run());
        cancel.setVisible(false); cancel.setManaged(false);
        var close = new Button("Voltar ao mapa"); close.setId("close-results"); close.setOnAction(e -> stage.hide());
        var spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(10, heading, spacer, cancel, copy, close); header.setAlignment(Pos.CENTER_LEFT);
        tabs.getTabs().setAll(tab("Resultado", result), tab("Comparação TSP", comparison));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setId("results-tabs");
        var root = new VBox(12, header, context, tabs, notice); root.setPadding(new Insets(24));
        root.getStyleClass().add("report-root"); VBox.setVgrow(tabs, Priority.ALWAYS);
        var scene = new Scene(root); scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm()); stage.setScene(scene);
        scene.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) stage.hide(); });
    }
    void show(OperationalSnapshot snapshot, boolean compare) {
        tabs.getSelectionModel().select(compare ? 1 : 0); stage.show(); update(snapshot); stage.toFront();
    }
    void update(OperationalSnapshot snapshot) {
        this.snapshot = snapshot;
        if (!stage.isShowing()) return;
        notice.setText("Tempos internos desta execução · geração e animação excluídas · não constitui benchmark.");
        context.setText(snapshot.n() + " refúgios  ·  seed " + snapshot.seed() + "  ·  instância #" + snapshot.instanceId()
                + (snapshot.phase().busy() ? "  ·  " + snapshot.stage() + " em andamento…" : ""));
        cancel.setVisible(snapshot.phase().busy()); cancel.setManaged(snapshot.phase().busy());
        cancel.setDisable(snapshot.phase() == OperationalSnapshot.Phase.CANCELAMENTO_SOLICITADO);
        renderResult(); renderComparison();
    }
    private void renderResult() {
        var report = ResultReport.of(snapshot);
        result.getChildren().clear();
        var title = text(report.title(), "report-section-title"); title.setId("report-algorithm");
        var status = text(report.status(), "report-badge"); status.setId("report-status");
        result.getChildren().addAll(new HBox(16, title, status), text(report.context(), "report-note"));
        var cost = stat(report.valueLabel(), report.value(), "report-cost");
        var time = stat("Tempo interno do solver", report.elapsed(), "report-time");
        var values = new HBox(16, cost, time); HBox.setHgrow(cost, Priority.ALWAYS); HBox.setHgrow(time, Priority.ALWAYS);
        cost.setMaxWidth(Double.MAX_VALUE); time.setMaxWidth(Double.MAX_VALUE);
        result.getChildren().addAll(values, text(report.conclusion(), "report-conclusion"), routeCard(report.routeLabel(), report.route()));
        if (!report.reason().isEmpty()) result.getChildren().add(text("Motivo: " + report.reason(), "report-note"));
        if (snapshot.result() != null) {
            var metrics = new TitledPane("Métricas e detalhes da execução", selectable(report.metrics()));
            metrics.setExpanded(false); metrics.setAnimated(false); metrics.setId("report-metrics"); result.getChildren().add(metrics);
        }
        if (snapshot.result() instanceof ExecutionRecord.Paths) result.getChildren().add(text(
                "O destino pode ser alterado no painel sem recalcular Dijkstra. A distância vem da matriz de custos; o terreno é somente visual.", "report-note"));
    }
    private void renderComparison() {
        var report = TspComparison.of(snapshot.instanceId(), snapshot.history());
        comparison.getChildren().clear();
        var headline = text(report.headline(), report.verdict() == TspComparison.Verdict.AGREEMENT ? "report-conclusion" : "report-section-title");
        headline.setId("comparison-verdict");
        comparison.getChildren().addAll(headline, text(report.explanation(), "report-context"));
        var brute = algorithmCard("Força Bruta", ExecutionRequest.Algorithm.BRUTE_FORCE, report.bruteForce());
        var back = algorithmCard("Backtracking", ExecutionRequest.Algorithm.BACKTRACKING, report.backtracking());
        var cards = new HBox(16, brute, back); HBox.setHgrow(brute, Priority.ALWAYS); HBox.setHgrow(back, Priority.ALWAYS);
        brute.setMaxWidth(Double.MAX_VALUE); back.setMaxWidth(Double.MAX_VALUE); brute.setPrefWidth(400); back.setPrefWidth(400);
        comparison.getChildren().add(cards);
        var options = snapshot.request().tspOptions();
        comparison.getChildren().addAll(text("Nova execução: até " + HudPresentation.optional(options.maxVisitedNodes()) + " nós e "
                + HudPresentation.optional(options.maxElapsedMillis()) + " ms. A instância é reutilizada; nenhuma cidade é regenerada.", "report-note"),
                text("Tempos são medições internas de uma execução, não um benchmark. Nós, rotas avaliadas e folhas descartadas têm significados próprios de cada algoritmo. Dijkstra resolve outro problema e não participa desta comparação.", "report-note"));
        var reading = new TitledPane("Como ler esta comparação", text(
                "Força Bruta enumera permutações; Backtracking elimina ramos por poda. Nós visitados contam permutações iniciadas na Força Bruta e entradas recursivas no Backtracking. "
                + "Rotas avaliadas contam ciclos completos calculados. Folhas descartadas contam ciclos eliminados por poda. Zero reportado é diferente de uma métrica não informada. "
                + "Os custos dos ciclos vêm da mesma matriz original, incluindo retorno à origem 0.", "report-context"));
        reading.setExpanded(false); reading.setAnimated(false); comparison.getChildren().add(reading);
        var history = snapshot.history().stream().filter(e -> e instanceof ExecutionRecord.Tsp && e.instanceId() == snapshot.instanceId()).toList();
        if (!history.isEmpty()) {
            var log = new TitledPane("Histórico TSP desta instância · " + history.size() + " tentativas", selectable(HudPresentation.history(history)));
            log.setExpanded(false); log.setAnimated(false); comparison.getChildren().add(log);
        }
    }
    private VBox algorithmCard(String name, ExecutionRequest.Algorithm algorithm, Optional<ExecutionRecord.Tsp> run) {
        var box = new VBox(6); box.setMinWidth(0); box.setMinHeight(Region.USE_PREF_SIZE); box.getStyleClass().add("comparison-card");
        box.setId(algorithm == ExecutionRequest.Algorithm.BRUTE_FORCE ? "compare-bruteforce" : "compare-backtracking");
        var nameLabel = text(name, "report-section-title");
        box.getChildren().add(nameLabel);
        if (run.isEmpty()) {
            box.getChildren().addAll(text("Sem resultado no histórico", "report-badge"), text("Execute para preencher este lado. O histórico mantém até oito tentativas da instância atual.", "report-context"));
        } else {
            var record = run.orElseThrow(); var r = record.result();
            box.getChildren().clear();
            var status = text(ResultReport.friendlyStatus(record.status()) + " · #" + record.runId(), "report-badge");
            status.setTooltip(new Tooltip("Orçamento usado: " + HudPresentation.optional(record.request().tspOptions().maxVisitedNodes()) + " nós · "
                    + HudPresentation.optional(record.request().tspOptions().maxElapsedMillis()) + " ms"));
            var heading = new HBox(10, nameLabel, status); heading.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(nameLabel, Priority.ALWAYS);
            box.getChildren().addAll(heading,
                    stat(r.optimalityGuaranteed() ? "Custo ótimo do ciclo" : "Custo encontrado", HudPresentation.optional(r.cost()), null),
                    text(r.optimalityGuaranteed() ? "Ótimo certificado" : "Sem garantia de ótimo", "report-context"),
                    metric("Tempo interno", HudPresentation.elapsed(r.metrics().elapsedNanos())),
                    metric("Nós visitados", HudPresentation.optional(r.metrics().visitedNodes())),
                    metric("Rotas avaliadas", HudPresentation.optional(r.metrics().completeRoutesEvaluated())),
                    metric("Folhas descartadas", r.metrics().discardedRoutes().map(Object::toString).orElse("não informado")),
                    routeCard("Ciclo encontrado", r.route().map(route -> HudPresentation.sequence(route.indices())).orElse("Nenhum ciclo completo.")));
            r.reason().ifPresent(reason -> box.getChildren().add(text("Motivo: " + reason, "report-note")));
        }
        var button = new Button((run.isEmpty() ? "Executar " : "Reexecutar ") + name);
        button.setId(algorithm == ExecutionRequest.Algorithm.BRUTE_FORCE ? "compare-run-bruteforce" : "compare-run-backtracking");
        button.setMaxWidth(Double.MAX_VALUE); button.getStyleClass().add("primary-action");
        boolean outside = snapshot.n() > snapshot.request().tspOptions().policy().recommendedMaxCities(algorithm.coreAlgorithm());
        button.setDisable(snapshot.problem() == null || snapshot.phase().busy() || outside);
        if (outside) box.getChildren().add(text("TSP grande: selecione este algoritmo no painel principal e confirme a busca limitada antes de executar.", "report-note"));
        button.setOnAction(e -> execute.accept(algorithm)); box.getChildren().add(button);
        return box;
    }
    private static Node metric(String name, String value) {
        var caption = text(name, "report-context"); var number = text(value, "report-metric");
        caption.setPrefWidth(140); caption.setMinWidth(100); HBox.setHgrow(caption, Priority.ALWAYS);
        number.setAlignment(Pos.CENTER_RIGHT); number.setPrefWidth(145);
        var line = new HBox(12, caption, number); line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("report-metric-row"); return line;
    }
    private static VBox stat(String caption, String value, String id) {
        var number = text(value, "report-value"); if (id != null) number.setId(id);
        var card = new VBox(7, text(caption, "report-context"), number); card.setMinWidth(0); card.getStyleClass().add("report-stat"); return card;
    }
    private static Node routeCard(String caption, String route) {
        var content = selectable(route);
        var box = new VBox(8, text(caption, "report-context"), content); box.getStyleClass().add("report-route-card"); return box;
    }
    private static TextArea selectable(String value) {
        var area = new TextArea(value); area.setEditable(false); area.setWrapText(true);
        area.setPrefRowCount(Math.min(7, Math.max(2, value.split("\n").length))); area.setMinHeight(54); return area;
    }
    private static Label text(String value, String style) {
        var label = new Label(value); label.setWrapText(true); label.setMinHeight(Region.USE_PREF_SIZE); label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE); label.getStyleClass().add(style); return label;
    }
    private static Tab tab(String title, Node content) {
        var scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        content.setStyle("-fx-padding: 12 8 16 0;"); scroll.getStyleClass().add("report-scroll"); return new Tab(title, scroll);
    }
}
