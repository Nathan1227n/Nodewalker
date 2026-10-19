package io.github.nathan1227n.nodewalker.app.world.render;

import io.github.nathan1227n.nodewalker.app.PreviewModeSelector;
import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import io.github.nathan1227n.nodewalker.app.world.WorldSceneModel;
import io.github.nathan1227n.nodewalker.app.world.WorldViewState;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/**
 * FX-thread-only presentation boundary for #12. Never generates a problem or starts a solver.
 * Models contain prepared immutable geometry. The current renderer exclusively owns its FX graph.
 */
public final class TspWorldView extends StackPane implements AutoCloseable {
    private final WorldViewState state = new WorldViewState();
    private final boolean supports3d;
    private boolean threeD;
    private boolean closed;
    private WorldRenderer renderer;
    private final ReadOnlyObjectWrapper<WorldSceneModel> presentedScene = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<TspSceneModel> presentedModel = new ReadOnlyObjectWrapper<>();
    private IntConsumer onSelection = ignored -> {
    };
    private Consumer<String> onMode = ignored -> {
    };

    public TspWorldView(boolean supports3d, boolean force2d) {
        requireFx();
        this.supports3d = supports3d;
        this.threeD = PreviewModeSelector.use3d(supports3d, force2d);
        getStyleClass().add("world-view");
        setFocusTraversable(true);
        setOnMousePressed(e -> requestFocus());
        setMinSize(320, 300);
    }

    public void setModel(WorldSceneModel model) {
        requireOpen();
        boolean sameProblem = state.model() != null && state.model().problem() == Objects.requireNonNull(model).problem();
        state.setModel(Objects.requireNonNull(model));
        if (sameProblem && renderer != null) renderer.resultChanged();
        else rebuild();
        onSelection.accept(state.selected());
        presentedScene.set(model);
        presentedModel.set(model instanceof TspSceneModel tsp ? tsp : null);
    }

    /**
     * Invalidate visible data while a different demo is loading; no old route is relabelled as new.
     */
    public void clear(String message) {
        requireOpen();
        disposeRenderer();
        state.clear();
        presentedModel.set(null);
        presentedScene.set(null);
        var label = new Label(message);
        label.getStyleClass().add("loading-message");
        getChildren().setAll(label);
    }

    public void setOnSelection(IntConsumer consumer) {
        requireOpen();
        onSelection = Objects.requireNonNull(consumer);
    }

    public void setOnMode(Consumer<String> consumer) {
        requireOpen();
        onMode = Objects.requireNonNull(consumer);
    }

    public int selectedIndex() {
        requireFx();
        return state.selected();
    }

    public ReadOnlyObjectProperty<TspSceneModel> modelProperty() {
        requireFx();
        return presentedModel.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<WorldSceneModel> sceneModelProperty() {
        requireFx();
        return presentedScene.getReadOnlyProperty();
    }

    public boolean isReady() { requireFx(); return renderer != null && renderer.ready(); }

    public boolean isThreeD() {
        requireFx();
        return threeD;
    }

    public void select(int index) {
        requireOpen();
        if (state.model() == null) return;
        state.select(index);
        renderer.refresh();
        onSelection.accept(index);
    }

    public void setThreeD(boolean value) {
        requireOpen();
        threeD = value && supports3d;
        if (state.model() != null) rebuild();
    }

    public void setSimplified(boolean value) {
        requireOpen();
        state.setSimplified(value);
        if (state.model() != null) rebuild();
    }

    public void setDecoration(boolean value) {
        requireOpen();
        state.setDecoration(value);
        if (renderer != null) renderer.refresh();
    }

    public void setConnections(boolean value) {
        requireOpen();
        state.setConnections(value);
        if (renderer != null) renderer.refresh();
    }

    public void setReducedLabels(boolean value) {
        requireOpen();
        state.setReducedLabels(value);
        if (renderer != null) renderer.refresh();
    }

    public void resetCamera() {
        requireOpen();
        if (renderer != null) {
            state.camera().reset();
            renderer.refresh();
        }
    }

    public void topView() {
        requireOpen();
        if (renderer != null) {
            state.camera().top();
            renderer.refresh();
        }
    }

    public void focusSelection() {
        requireOpen();
        if (renderer != null) {
            state.camera().focus(state.model().refuges().get(state.selected()).position());
            renderer.refresh();
        }
    }

    private void rebuild() {
        disposeRenderer();
        String diagnostic = "";
        if (threeD) {
            try {
                renderer = new World3DRenderer(state, this::select);
            } catch (RuntimeException | LinkageError failure) {
                threeD = false;
                diagnostic = " · Scene3D falhou: " + failure.getClass().getSimpleName();
                System.err.println("NodeWalker: fallback 2D após falha ao criar Scene3D: " + failure);
            }
        }
        if (!threeD) renderer = new Map2DRenderer(state, this::select);
        getChildren().setAll(renderer.node());
        onMode.accept((threeD ? "3D · XYZ lógico preservado" : "2D · projeção XZ / altitude Y na seleção") + diagnostic);
    }

    private void disposeRenderer() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        getChildren().clear();
    }

    @Override
    public void close() {
        requireFx();
        if (closed) return;
        disposeRenderer();
        setOnMousePressed(null);
        state.clear();
        presentedModel.set(null);
        presentedScene.set(null);
        onSelection = ignored -> {
        };
        onMode = ignored -> {
        };
        closed = true;
    }

    private void requireOpen() {
        requireFx();
        if (closed) throw new IllegalStateException("View is closed");
    }

    private static void requireFx() {
        if (!Platform.isFxApplicationThread())
            throw new IllegalStateException("TspWorldView must be accessed on the FX Application Thread");
    }
}
