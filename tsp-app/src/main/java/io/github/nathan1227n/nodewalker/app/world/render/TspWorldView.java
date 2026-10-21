package io.github.nathan1227n.nodewalker.app.world.render;

import io.github.nathan1227n.nodewalker.app.PreviewModeSelector;
import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import io.github.nathan1227n.nodewalker.app.world.WorldSceneModel;
import io.github.nathan1227n.nodewalker.app.world.WorldViewState;
import io.github.nathan1227n.nodewalker.app.world.WorldPlayback;
import io.github.nathan1227n.nodewalker.app.world.EdgeWeightMode;
import io.github.nathan1227n.nodewalker.app.world.EdgeWeightPolicy;
import io.github.nathan1227n.nodewalker.app.world.CostBreakdown;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javafx.application.Platform;
import javafx.animation.AnimationTimer;
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
    private long lastFrame;
    private Consumer<String> onPlayback = ignored -> { };
    private String lastPlaybackLabel = "";
    private final AnimationTimer playbackTimer = new AnimationTimer() {
        @Override public void handle(long now) {
            if (renderer == null || !renderer.ready()) { lastFrame = 0; return; }
            if (lastFrame != 0) state.playback().advance(Math.min(now - lastFrame, 100_000_000));
            lastFrame = now;
            renderer.playbackChanged();
            announcePlayback();
            if (!state.playback().active() || state.playback().paused()) { stop(); lastFrame = 0; }
        }
    };
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
        startPlayback();
        onSelection.accept(state.selected());
        presentedScene.set(model);
        presentedModel.set(model instanceof TspSceneModel tsp ? tsp : null);
    }

    /**
     * Invalidate visible data while a different demo is loading; no old route is relabelled as new.
     */
    public void clear(String message) {
        requireOpen();
        playbackTimer.stop(); lastFrame = 0;
        disposeRenderer();
        state.clear();
        announcePlayback();
        presentedModel.set(null);
        presentedScene.set(null);
        var label = new Label(message);
        label.getStyleClass().add("loading-message");
        getChildren().setAll(label);
    }

    public void setOnPlayback(Consumer<String> consumer) { requireOpen(); onPlayback = Objects.requireNonNull(consumer); }
    private void announcePlayback() {
        String next = state.model() == null ? "" : state.playback().label();
        var breakdown=CostBreakdown.ofScene(state.model());
        if(breakdown.isPresent() && breakdown.orElseThrow().total().isPresent())
            next += "\n" + breakdown.orElseThrow().replayText(state.playback().completedEdges());
        if (!next.equals(lastPlaybackLabel)) { lastPlaybackLabel = next; onPlayback.accept(next); }
    }
    private void startPlayback() {
        lastFrame = 0;
        if (state.playback().active() && !state.playback().paused()) playbackTimer.start();
        else playbackTimer.stop();
        announcePlayback();
    }
    public boolean hasPlaybackPath() { requireFx(); return state.playback().edgeCount() > 0; }
    public boolean isAnimating() { requireFx(); return state.playback().active(); }
    public boolean isPlaybackPaused() { requireFx(); return state.playback().paused(); }
    public WorldPlayback.Stage playbackStage() { requireFx(); return state.playback().stage(); }
    public int visibleRefuges() { requireFx(); return state.playback().completedCities(); }
    public double pathEdgeFraction(int edge) { requireFx(); return state.edgeFraction(edge); }
    public int visiblePathEdges() { requireFx(); return state.playback().completedEdges(); }
    public void pausePlayback() { requireOpen(); state.playback().togglePause(); startPlayback(); }
    public void replayPath() { requireOpen(); state.playback().replay(); if (renderer != null) renderer.playbackChanged(); startPlayback(); }
    public void setPlaybackSpeed(double speed) { requireOpen(); state.playback().speed(speed); }
    public void finishPlayback() {
        requireOpen(); state.playback().finish(); playbackTimer.stop(); lastFrame = 0;
        if (renderer != null) renderer.playbackChanged(); announcePlayback();
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
        state.playback().revealCities();
        startPlayback();
        renderer.refresh();
        onSelection.accept(index);
    }

    public void setThreeD(boolean value) {
        requireOpen();
        threeD = value && supports3d;
        if (state.model() != null) { rebuild(); startPlayback(); }
    }

    public void setSimplified(boolean value) {
        requireOpen();
        state.setSimplified(value);
        if (state.model() != null) { rebuild(); startPlayback(); }
    }

    public void setDecoration(boolean value) {
        requireOpen();
        state.setDecoration(value);
        if (renderer != null) renderer.refresh();
    }

    public void setWeightMode(EdgeWeightMode mode) {
        requireOpen(); state.setWeightMode(mode); if(renderer!=null)renderer.refresh();
    }
    public void setWeightScope(EdgeWeightPolicy.Scope scope) {
        requireOpen(); state.setWeightScope(scope); if(renderer!=null)renderer.refresh();
    }
    public int visualConnectionCount(){requireFx();return state.weightSelection().renderedEdges();}
    public EdgeWeightMode weightMode(){requireFx();return state.weightMode();}
    public EdgeWeightPolicy.Scope weightScope(){requireFx();return state.weightScope();}

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
        playbackTimer.stop(); lastFrame = 0;
        disposeRenderer();
        setOnMousePressed(null);
        state.clear();
        presentedModel.set(null);
        presentedScene.set(null);
        onSelection = ignored -> {
        };
        onMode = ignored -> {
        };
        onPlayback = ignored -> { };
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
