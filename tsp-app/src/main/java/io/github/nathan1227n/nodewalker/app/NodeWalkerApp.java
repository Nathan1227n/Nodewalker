package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.core.SearchSpace;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Demonstração técnica G0. Ainda não executa um solver TSP. */
public final class NodeWalkerApp extends Application {
    private StackPane preview;
    private Label modeLabel;

    @Override
    public void start(Stage stage) {
        boolean supports3d = Platform.isSupported(ConditionalFeature.SCENE3D);
        boolean force2d = getParameters().getRaw().contains("--2d");

        Label title = new Label("NODEWALKER");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("TSP • Route Optimization • JavaFX 3D");
        subtitle.getStyleClass().add("muted");

        VBox top = new VBox(6, title, subtitle);
        top.setPadding(new Insets(0, 0, 16, 0));

        preview = new StackPane();
        preview.getStyleClass().add("preview-panel");
        preview.setMinSize(400, 340);

        modeLabel = new Label();
        modeLabel.getStyleClass().add("muted");

        Button button3d = new Button("Visualização 3D");
        button3d.setDisable(!supports3d);
        button3d.setMaxWidth(Double.MAX_VALUE);
        button3d.setOnAction(event -> setPreview(DemoPreview.create3d(), "3D — recurso disponível"));

        Button button2d = new Button("Alternativa 2D");
        button2d.setMaxWidth(Double.MAX_VALUE);
        button2d.setOnAction(event -> setPreview(DemoPreview.create2d(), "2D — visualização alternativa"));

        Label section = new Label("FUNDAÇÃO • G0");
        section.getStyleClass().add("section-title");
        Label phase = new Label("Demonstração gráfica inicial. As cidades são fictícias; os solvers serão implementados no G1/G2.");
        phase.setWrapText(true);
        phase.getStyleClass().add("muted");

        Label routes = new Label("Exemplo N = 5: (N−1)! = " + SearchSpace.completeRoutes(5) + " rotas");
        routes.setWrapText(true);

        Label supportInfo = new Label(supports3d
                ? "Scene3D suportado nesta execução."
                : "Scene3D indisponível: utilizar visualização 2D.");
        supportInfo.setWrapText(true);
        supportInfo.getStyleClass().add("muted");

        VBox controls = new VBox(14, section, phase, routes, button3d, button2d, modeLabel, supportInfo);
        controls.getStyleClass().add("control-panel");
        controls.setPrefWidth(290);
        controls.setPadding(new Insets(18));

        HBox footer = new HBox(new Label("G0: Maven + Java 21 + JavaFX 21.0.12 + JUnit 5.14.3"));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(14, 0, 0, 0));
        footer.getStyleClass().add("muted");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(22));
        root.setTop(top);
        root.setCenter(preview);
        root.setRight(controls);
        root.setBottom(footer);
        BorderPane.setMargin(controls, new Insets(0, 0, 0, 16));

        Scene scene = new Scene(root, 1080, 660);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("NodeWalker | G0 — Fundação");
        stage.setMinWidth(850);
        stage.setMinHeight(560);
        stage.show();

        if (supports3d && !force2d) {
            setPreview(DemoPreview.create3d(), "3D — recurso disponível");
        } else {
            setPreview(DemoPreview.create2d(), supports3d
                    ? "2D — inicializado com --2d"
                    : "2D — fallback por falta de suporte a Scene3D");
        }
    }

    private void setPreview(Node content, String mode) {
        preview.getChildren().setAll(content);
        modeLabel.setText(mode);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
