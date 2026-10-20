package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.operation.*;
import io.github.nathan1227n.nodewalker.app.world.WorldPlayback;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.shape.Cylinder;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Real production controls/solver, captures the actual animated intermediate frames. Not a search trace. */
public final class PlaybackVisualReview {
    private static final CountDownLatch finished = new CountDownLatch(1);
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final List<String> rows = new ArrayList<>();
    private static NodeWalkerApp app;
    private static Stage stage;
    private static TspWorldView view;
    private static Path output;
    private static boolean threeD;
    private static ExecutionRecord result;
    private static long run;

    public static void main(String[] args) throws Exception {
        output = Path.of(args[0]); Files.createDirectories(output);
        boolean force2d = Arrays.asList(args).contains("--2d-only");
        rows.add("capture\tmethod\tinstance\trun\tsolverStatus\tplaybackStage\tpaused\tcompleteCities\tcompleteEdges\tvisible3DStrokes\tcost\twidth\theight");
        Platform.startup(() -> guarded(() -> {
            app = new NodeWalkerApp(); stage = new Stage(); app.start(stage, force2d ? List.of("--2d") : List.of(), Map.of());
            view = (TspWorldView) stage.getScene().lookup("#tsp-world"); threeD = view.isThreeD();
            if (force2d) require(!threeD, "explicit 2D does not create Scene3D");
            if (Arrays.asList(args).contains("--fallback-only")) {
                require(!Platform.isSupported(javafx.application.ConditionalFeature.SCENE3D), "software fallback is actually unsupported");
                require(node("mode-3d").isDisabled() && !threeD, "fallback disables 3D");
            }
            when(() -> stage.isShowing(), () -> {
                require(!((TitledPane) node("operational-details")).isExpanded(), "details start closed");
                capture("00-simple-start"); click("generate");
                when(() -> view.isReady() && view.playbackStage() == WorldPlayback.Stage.REFUGES && view.visibleRefuges() >= 2, () -> {
                    click("playback-pause"); require(view.isPlaybackPaused(), "pause refuge formation");
                    capture("01-refuges-being-revealed"); click("playback-finish"); require(view.visibleRefuges() == 5, "finish refuge formation");
                    click("execute"); when(() -> s().result() != null && view.isReady() && view.playbackStage() == WorldPlayback.Stage.PATH, () -> {
                        result = s().result(); run = s().runId();
                        require(result.status().equals("CONCLUIDO"), "solver really completed before replay");
                        require(view.visiblePathEdges() < 5, "result must not appear fully drawn immediately");
                        click("playback-pause"); capture("02-solver-done-path-still-forming"); click("playback-pause");
                        when(() -> view.visiblePathEdges() == 1, PlaybackVisualReview::midPath);
                    });
                });
            });
        }));
        if (!finished.await(60, TimeUnit.SECONDS)) { Platform.exit(); throw new AssertionError("Playback review timeout"); }
        if (failure.get() != null) throw new AssertionError("Playback graphical review failed", failure.get());
        System.out.println("PLAYBACK PASS: gradual points/edges, pause/resume/speed/replay, 3D/2D, no recomputation, new instance invalidation.");
    }
    private static void midPath() {
        click("playback-pause"); double fraction = view.pathEdgeFraction(1);
        if (threeD) {
            var strokes = strokes(view);
            long visible = strokes.stream().filter(Node::isVisible).count();
            require(visible > 0 && visible < 5, "actual 3D route is partially visible");
            var current = strokes.stream().filter(n -> Integer.valueOf(1).equals(n.getUserData())).map(n -> (Cylinder) n).findFirst().orElseThrow();
            require(current.getHeight() < s().scene().segments().get(1).geometry().length(), "actual cylinder grows instead of appearing whole");
        }
        afterPulses(5, () -> {
            require(view.pathEdgeFraction(1) == fraction, "pause freezes presentation clock");
            require(s().result() == result && s().runId() == run, "pause is not solver cancellation");
            capture("03-one-edge-drawn-paused");
            var geometry = s().scene().preparedWorld(); view.setThreeD(false);
            when(view::isReady, () -> {
                require(view.isPlaybackPaused() && view.pathEdgeFraction(1) == fraction, "mode switch preserves playback");
                require(s().scene().preparedWorld() == geometry, "mode switch shares world"); capture("04-same-partial-path-2d");
                if (threeD) view.setThreeD(true);
                when(view::isReady, () -> {
                    click("playback-replay"); click("playback-pause"); require(view.visiblePathEdges() == 0, "replay resets drawing");
                    combo("playback-speed", 4.0); click("playback-pause");
                    when(() -> !view.isAnimating(), () -> {
                        require(view.visiblePathEdges() == 5, "all five tour edges including return");
                        require(s().result() == result && s().runId() == run && s().history().size() == 1, "replay must not invoke the solver");
                        capture("05-complete-path-simple-hud");
                        stage.setWidth(960); stage.setHeight(680); view.setSimplified(true);
                        when(view::isReady, () -> {
                            capture("06-small-window"); click("playback-replay"); click("generate");
                            when(() -> s().instanceId() == 2 && s().scene() != null && view.isReady(), () -> {
                                require(s().result() == null && !view.hasPlaybackPath(), "new instance removes old route/replay");
                                view.finishPlayback(); capture("07-new-instance-no-old-route"); finish();
                            });
                        });
                    });
                });
            });
        });
    }
    private static OperationalSnapshot s() { return app.controllerForReview().snapshot(); }
    private static Node node(String id) { return Objects.requireNonNull(stage.getScene().lookup("#" + id), id); }
    private static void click(String id) { var button = (Button) node(id); require(!button.isDisabled(), id + " disabled"); button.fire(); }
    @SuppressWarnings("unchecked") private static <T> void combo(String id, T value) { ((ComboBox<T>) node(id)).setValue(value); }
    private static void when(BooleanSupplier condition, Runnable action) {
        new AnimationTimer() {
            final long start = System.nanoTime();
            public void handle(long now) {
                try {
                    if (condition.getAsBoolean()) { stop(); Platform.runLater(() -> guarded(action)); }
                    else if (System.nanoTime() - start > TimeUnit.SECONDS.toNanos(15)) { stop(); throw new AssertionError("wait timed out"); }
                } catch (Throwable t) { stop(); fail(t); }
            }
        }.start();
    }
    private static void afterPulses(int count, Runnable action) { int[] pulses = {0}; when(() -> ++pulses[0] >= count, action); }
    private static void capture(String name) {
        try {
            stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
            var shot = stage.getScene().snapshot(null); int w = (int) shot.getWidth(), h = (int) shot.getHeight(); int[] pixels = new int[w * h];
            shot.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);
            var image = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB); image.setRGB(0,0,w,h,pixels,0,w); ImageIO.write(image,"png",output.resolve(name + ".png").toFile());
            rows.add(name + "\treal-solver-presentation-playback\t" + s().instanceId() + "\t" + s().runId() + "\t" + (s().result() == null ? "none" : s().result().status())
                    + "\t" + view.playbackStage() + "\t" + view.isPlaybackPaused() + "\t" + view.visibleRefuges() + "\t" + view.visiblePathEdges()
                    + "\t" + strokes(view).stream().filter(Node::isVisible).count() + "\t" + HudPresentation.of(s()).cost() + "\t" + w + "\t" + h);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static List<Node> strokes(Node root) {
        var found = new ArrayList<Node>();
        if (root.getStyleClass().contains("route-stroke")) found.add(root);
        if (root instanceof javafx.scene.SubScene sub) found.addAll(strokes(sub.getRoot()));
        if (root instanceof javafx.scene.Parent parent) for (var child : parent.getChildrenUnmodifiable()) found.addAll(strokes(child));
        return found;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void guarded(Runnable action) { try { action.run(); } catch (Throwable t) { fail(t); } }
    private static void fail(Throwable t) { failure.compareAndSet(null,t); t.printStackTrace(); finish(); }
    private static void finish() {
        try { Files.write(output.resolve("playback-manifest.tsv"),rows); }
        catch (Exception e) { failure.compareAndSet(null,e); }
        finally { if (app != null) app.stop(); if (stage != null) stage.close(); Platform.exit(); finished.countDown(); }
    }
}
