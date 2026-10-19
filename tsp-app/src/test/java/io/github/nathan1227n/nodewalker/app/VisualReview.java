package io.github.nathan1227n.nodewalker.app;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import io.github.nathan1227n.nodewalker.app.world.render.TspWorldView;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SubScene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.robot.Robot;
import javafx.scene.transform.MatrixType;
import javafx.stage.Stage;

/**
 * Opt-in real-window review, NEVER run by Surefire/CI. Uses the production composition and GPU snapshots.
 * It exercises API-driven controls; it is not a measurement of presented FPS or a human usability review.
 */
public final class VisualReview {
    private static final List<String> rows = new ArrayList<>();
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final CountDownLatch finished = new CountDownLatch(1);
    private static NodeWalkerApp app;
    private static Stage stage;
    private static TspWorldView view;
    private static Path directory;
    private static boolean only2d;
    private static boolean nativeInput;
    private static List<TspSceneModel> stateFixtures;
    private static final List<String> resources = new ArrayList<>();
    private static long started;

    public static void main(String[] args) throws Exception {
        directory = Path.of(args.length == 0 ? "tsp-app/target/visual-review" : args[0]);
        boolean forced2d = java.util.Arrays.asList(args).contains("--2d-only");
        boolean fallbackOnly = java.util.Arrays.asList(args).contains("--fallback-only");
        only2d = forced2d || fallbackOnly;
        nativeInput = java.util.Arrays.asList(args).contains("--native-input");
        Files.createDirectories(directory);
        started = System.nanoTime();
        stateFixtures = prepareStateFixtures();
        resources.add("capture\tnodes\tmaterials\ttriangleUpperBound\tnumericBuffers\theapUsedMiB\trssKiB\telapsedSinceReviewStartMs\tplants\ttrees\tdeadTrees\tshrubs\tgrass\tboulders");
        rows.add("capture\tN\tseed\tstatus\tcost\tvisitedNodes\troute\tselected\tmode\twidth\theight");
        Platform.startup(() -> guarded(() -> {
            app = new NodeWalkerApp();
            stage = new Stage();
            app.start(stage, forced2d ? List.of("--2d") : List.of(), Map.of("demo", "5"));
            view = (TspWorldView) stage.getScene().lookup("#tsp-world");
            if (fallbackOnly) {
                require(!Platform.isSupported(javafx.application.ConditionalFeature.SCENE3D), "Software fallback check unexpectedly supports 3D");
                require(stage.getScene().lookup("#mode-3d").isDisabled(), "3D button must be disabled without Scene3D");
            }
            if (only2d) {
                onModel(5, () -> {
                    require(!view.isThreeD(), "--2d must avoid Scene3D creation");
                    capture("01-n5-software2d");
                    showDemo(DemoCatalog.TRIANGLE, "02-n3-software2d", () -> showDemo(DemoCatalog.VALLEY, "03-n8-software2d", () ->
                            showDemo(DemoCatalog.FRONTIER, "04-n12-software2d", VisualReview::finish)));
                });
                return;
            }
            onModel(5, () -> {
                if (nativeInput) exerciseNativeInput(VisualReview::reviewStandard);
                else reviewStandard();
            });
        }));
        finished.await();
        if (failure.get() != null) throw new AssertionError("Graphical review failed", failure.get());
        System.out.println("Real-window captures and manifest: " + directory.toAbsolutePath());
    }

    private static void reviewStandard() {
        exerciseHandlers();
        capture("01-n5-overview");
        view.select(2);
        view.topView();
        afterPulse(() -> {
            capture("02-n5-top-selection");
            SubScene retired = findSubScene(view);
            var retainedModel = view.modelProperty().get();
            view.setThreeD(false);
            afterPulse(() -> {
                require(view.selectedIndex() == 2, "Mode change lost selection");
                require(view.modelProperty().get() == retainedModel, "Mode change replaced model/result");
                require(retired.getCamera() == null && ((Parent) retired.getRoot()).getChildrenUnmodifiable().isEmpty(), "Retired graph retained");
                capture("03-n5-map2d");
                view.setThreeD(true);
                view.resetCamera();
                view.setSimplified(true);
                view.setDecoration(false);
                afterPulse(() -> {
                    capture("04-n5-simplified");
                    view.setSimplified(false);
                    view.setDecoration(true);
                    view.select(0);
                    view.focusSelection();
                    afterPulse(() -> {
                        capture("05-n5-base-detail");
                        showDemo(DemoCatalog.TRIANGLE, "06-n3", () -> showDemo(DemoCatalog.VALLEY, "07-n8", () ->
                                showDemo(DemoCatalog.FRONTIER, "08-n12-incumbent", VisualReview::reviewRefinement)));
                    });
                });
            });
        });
    }

    private static void exerciseHandlers() {
        SubScene scene3d = findSubScene(view);
        require(scene3d != null, "3D review requires Scene3D support; use --software-2d otherwise");
        String before = cameraMatrix(scene3d);
        javafx.event.Event.fireEvent(scene3d, mouse(MouseEvent.MOUSE_PRESSED, 250, 150, true));
        javafx.event.Event.fireEvent(scene3d, mouse(MouseEvent.MOUSE_DRAGGED, 310, 180, true));
        javafx.event.Event.fireEvent(scene3d, mouse(MouseEvent.MOUSE_RELEASED, 310, 180, false));
        require(!before.equals(cameraMatrix(scene3d)), "Viewport drag handler failed");
        String orbit = cameraMatrix(scene3d);
        javafx.event.Event.fireEvent(scene3d, new ScrollEvent(ScrollEvent.SCROLL, 310, 180, 310, 180,
                false, false, false, false, false, false, 0, 80, 0, 80,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0,
                new PickResult(scene3d, 310, 180)));
        require(!orbit.equals(cameraMatrix(scene3d)), "Viewport scroll handler failed");
        view.resetCamera();
        require(before.equals(cameraMatrix(scene3d)), "Reset failed");
        var current = view.modelProperty().get();
        view.setModel(current.withResult(current.result()));
        require(scene3d == findSubScene(view), "Result-only update rebuilt terrain/SubScene");
        System.out.println("CONTROL HANDLERS PASS (synthetic events): orbit, zoom, reset; result update retains SubScene");
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y, boolean down) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false, down, false, false, true, false, false, null);
    }

    private static void exerciseNativeInput(Runnable next) {
        stage.toFront();
        stage.requestFocus();
        SubScene scene3d = findSubScene(view);
        require(scene3d != null, "Native 3D check requires Scene3D support");
        var robot = new Robot();
        var area = scene3d.localToScreen(scene3d.getBoundsInLocal());
        double x = area.getMinX() + area.getWidth() * .37, y = area.getMinY() + area.getHeight() * .2;
        String before = cameraMatrix(scene3d);
        robot.mouseMove(x, y);
        robot.mousePress(MouseButton.PRIMARY);
        afterPulse(() -> {
            robot.mouseMove(x + 90, y + 36);
            afterPulse(() -> {
                robot.mouseRelease(MouseButton.PRIMARY);
                afterPulse(() -> {
                    require(!before.equals(cameraMatrix(scene3d)), "Native orbital drag did not update camera");
                    String orbit = cameraMatrix(scene3d);
                    robot.mouseWheel(3);
                    afterPulse(() -> {
                        require(!orbit.equals(cameraMatrix(scene3d)), "Native wheel did not update zoom");
                        Label city2 = view.lookupAll(".world-label").stream().filter(n -> n instanceof Label l && Integer.valueOf(2).equals(l.getUserData()))
                                .map(n -> (Label) n).findFirst().orElseThrow();
                        var bounds = city2.localToScreen(city2.getBoundsInLocal());
                        robot.mouseMove(bounds.getCenterX(), bounds.getCenterY());
                        robot.mousePress(MouseButton.PRIMARY);
                        robot.mouseRelease(MouseButton.PRIMARY);
                        afterPulse(() -> {
                            require(view.selectedIndex() == 2, "Native refuge selection failed");
                            capture("00-native-orbit-zoom-selection");
                            robot.keyPress(KeyCode.T);
                            robot.keyRelease(KeyCode.T);
                            afterPulse(() -> {
                                require(Math.abs(scene3d.getCamera().getLocalToSceneTransform().getMyz() - 1) < 1e-9, "Native top-view shortcut failed");
                                robot.keyPress(KeyCode.R);
                                robot.keyRelease(KeyCode.R);
                                afterPulse(() -> {
                                    require(before.equals(cameraMatrix(scene3d)), "Native reset shortcut failed");
                                    System.out.println("NATIVE INPUT PASS: orbital drag, wheel zoom, refuge click, T top view, R reset");
                                    view.select(0);
                                    next.run();
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static String cameraMatrix(SubScene scene3d) {
        return java.util.Arrays.toString(scene3d.getCamera().getLocalToSceneTransform().toArray(MatrixType.MT_3D_3x4));
    }

    private static SubScene findSubScene(Node node) {
        if (node instanceof SubScene subScene) return subScene;
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            SubScene found = findSubScene(child);
            if (found != null) return found;
        }
        return null;
    }

    private static void reviewRefinement() {
        view.setReducedLabels(true);
        afterPulse(() -> {
            capture("09-n12-clean-labels");
            view.setReducedLabels(false);
            showDemo(DemoCatalog.OUTPOSTS, "10-n5-before-state-check", () -> reviewState(0));
        });
    }

    private static List<TspSceneModel> prepareStateFixtures() {
        var p = io.github.nathan1227n.nodewalker.core.generator.TspGenerator.generate(5, 23,
                new io.github.nathan1227n.nodewalker.core.generator.GenerationOptions(10000, 1000000));
        var result = new io.github.nathan1227n.nodewalker.core.solver.BacktrackingSolver().solve(p,
                io.github.nathan1227n.nodewalker.core.solver.SolverExecutionOptions.nodeLimit(50000), new io.github.nathan1227n.nodewalker.core.solver.SolverCancellationToken());
        var base = TspSceneModel.of(p, java.util.Optional.of(result));
        var fixtures = new ArrayList<TspSceneModel>();
        fixtures.add(base);
        for (var status : List.of(io.github.nathan1227n.nodewalker.core.solver.SolverStatus.CANCELADO,
                io.github.nathan1227n.nodewalker.core.solver.SolverStatus.LIMITE_ATINGIDO, io.github.nathan1227n.nodewalker.core.solver.SolverStatus.ERRO)) {
            for (boolean incumbent : new boolean[]{true, false})
                fixtures.add(base.withResult(java.util.Optional.of(
                        io.github.nathan1227n.nodewalker.core.solver.TspResult.interrupted(p, status, incumbent ? result.route() : java.util.Optional.empty(),
                                incumbent ? result.cost() : java.util.OptionalLong.empty(), io.github.nathan1227n.nodewalker.core.solver.SolverMetrics.empty(),
                                "FIXTURE DE APRESENTAÇÃO: " + status + " / incumbente=" + incumbent))));
        }
        return List.copyOf(fixtures);
    }

    private static void reviewState(int index) {
        if (index == stateFixtures.size()) {
            app.present(stateFixtures.getFirst());
            afterPulse(() -> {
                capture("18-n5-reset-state");
                reviewVariant(1);
            });
            return;
        }
        var m = stateFixtures.get(index);
        app.present(m);
        afterPulse(() -> {
            capture(String.format("%02d-state-fixture-%s-%s", 11 + index, m.statusLabel(), m.segments().isEmpty() ? "none" : "route"));
            require(((Label) stage.getScene().lookup("#route-kind")).getText().equals(m.presentation().headline()), "HUD route-kind mismatch");
            view.setThreeD(false);
            require(view.modelProperty().get() == m, "State lost when switching mode");
            view.setThreeD(true);
            reviewState(index + 1);
        });
    }

    private static void reviewVariant(int index) {
        if (index == 4) {
            finish();
            return;
        }
        view.select(index);
        view.focusSelection();
        afterPulse(() -> {
            capture("variant-" + index);
            reviewVariant(index + 1);
        });
    }

    private static String resourceRow(String name, TspSceneModel m) {
        var stats = new ResourceStats();
        countNodes(view, stats);
        require(stats.nodes <= 800, "Viewport Node budget exceeded: " + stats.nodes);
        require(stats.materials.size() <= 20, "Material budget exceeded: " + stats.materials.size());
        require(stats.triangles <= 50000, "Triangle upper-bound budget exceeded: " + stats.triangles);
        var runtime = Runtime.getRuntime();
        long heap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024), rss = -1;
        try {
            rss = Files.readAllLines(Path.of("/proc/self/status")).stream().filter(l -> l.startsWith("VmRSS:"))
                    .map(l -> l.replaceAll("[^0-9]", "")).mapToLong(Long::parseLong).findFirst().orElse(-1);
        } catch (Exception ignored) { /* unavailable outside Linux; never report a fabricated zero */ }
        var geometry = m.geometry(name.contains("simplified"));
        StringBuilder vegetationCounts = new StringBuilder("\t" + geometry.vegetation().plants().size());
        for (var kind : io.github.nathan1227n.nodewalker.app.world.VegetationFactory.Kind.values())
            vegetationCounts.append('\t').append(geometry.vegetation().plants().stream().filter(p -> p.kind() == kind).count());
        return name + "\t" + stats.nodes + "\t" + stats.materials.size() + "\t" + stats.triangles + "\t" +
                geometry.numericBufferBytes() + "\t" + heap + "\t" + rss + "\t" + (System.nanoTime() - started) / 1000000 + vegetationCounts;
    }

    private static final class ResourceStats {
        int nodes;
        long triangles;
        final java.util.Set<javafx.scene.paint.Material> materials = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    }

    private static void countNodes(Node node, ResourceStats stats) {
        stats.nodes++;
        if (node instanceof javafx.scene.shape.Shape3D shape) {
            if (shape.getMaterial() != null) stats.materials.add(shape.getMaterial());
            if (shape instanceof javafx.scene.shape.MeshView view && view.getMesh() instanceof javafx.scene.shape.TriangleMesh mesh)
                stats.triangles += mesh.getFaces().size() / mesh.getFaceElementSize();
            else if (shape instanceof javafx.scene.shape.Cylinder cylinder)
                stats.triangles += cylinder.getDivisions() * 4L;
            else if (shape instanceof javafx.scene.shape.Sphere sphere)
                stats.triangles += 2L * sphere.getDivisions() * sphere.getDivisions();
        }
        if (node instanceof SubScene subScene) countNodes(subScene.getRoot(), stats);
        if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) countNodes(child, stats);
    }

    @SuppressWarnings("unchecked")
    private static void showDemo(DemoCatalog demo, String name, Runnable next) {
        onModel(demo.cityCount(), () -> {
            capture(name);
            next.run();
        });
        ((ComboBox<DemoCatalog>) stage.getScene().lookup("#demo-catalog")).setValue(demo);
    }

    private static void onModel(int n, Runnable action) {
        var listener = new javafx.beans.value.ChangeListener<TspSceneModel>() {
            boolean executionRequested;
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends TspSceneModel> source,
                                TspSceneModel old, TspSceneModel next) {
                if (next != null && next.problem().size() == n) {
                    if (next.result().isEmpty()) {
                        if (!executionRequested) {
                            executionRequested = true;
                            ((javafx.scene.control.CheckBox) stage.getScene().lookup("#acknowledge-expensive")).setSelected(true);
                            ((javafx.scene.control.Button) stage.getScene().lookup("#execute")).fire();
                        }
                        return;
                    }
                    view.modelProperty().removeListener(this);
                    afterPulse(action);
                }
            }
        };
        view.modelProperty().addListener(listener);
        if (view.modelProperty().get() != null && view.modelProperty().get().problem().size() == n)
            listener.changed(view.modelProperty(), null, view.modelProperty().get());
    }

    private static void afterPulse(Runnable action) {
        new AnimationTimer() {
            int pulses;

            @Override
            public void handle(long now) {
                if (++pulses >= 3 && view.isReady()) {
                    stop();
                    guarded(action);
                }
            }
        }.start();
    }

    private static void capture(String name) {
        try {
            var image = stage.getScene().snapshot(null);
            int w = (int) image.getWidth(), h = (int) image.getHeight();
            int[] pixels = new int[w * h];
            image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
            var output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            output.setRGB(0, 0, w, h, pixels, 0, w);
            ImageIO.write(output, "png", directory.resolve(name + ".png").toFile());
            var m = view.modelProperty().get();
            var certification = (Label) stage.getScene().lookup("#certification");
            require(certification.getText().equals(m.presentation().certification()), "HUD certification differs from result");
            require(!certification.isWrapText(), "Certification must not split its negation");
            require(((Label) stage.getScene().lookup("#solver-status")).getText().equals(m.statusLabel()), "HUD state mismatch");
            var visibleLabels = view.lookupAll(".world-label").stream().filter(Node::isVisible).toList();
            var viewport = view.localToScene(view.getBoundsInLocal());
            for (int i = 0; i < visibleLabels.size(); i++) {
                var label = visibleLabels.get(i);
                var bounds = label.localToScene(label.getBoundsInLocal());
                require(viewport.contains(bounds), "Label outside viewport: " + label.getUserData());
                for (int j = 0; j < i; j++)
                    require(!bounds.intersects(visibleLabels.get(j).localToScene(visibleLabels.get(j).getBoundsInLocal())),
                            "Visible label collision: " + label.getUserData() + " / " + visibleLabels.get(j).getUserData());
            }
            resources.add(resourceRow(name, m));
            require(m.refuges().size() == m.problem().size(), "Wrong refuge count");
            require(m.segments().isEmpty() || m.segments().size() == m.problem().size(), "Wrong cycle");
            rows.add(name + "\t" + m.problem().size() + "\t" + m.problem().seed().orElseThrow() + "\t" + m.statusLabel()
                    + "\t" + (m.cost().isPresent() ? m.cost().getAsLong() : "absent") + "\t" + m.metrics().visitedNodes().orElse(-1)
                    + "\t" + m.routeIndices() + "\t" + view.selectedIndex() + "\t" + (view.isThreeD() ? "3D" : "2D") + "\t" + w + "\t" + h);
            System.out.println(rows.getLast());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void guarded(Runnable action) {
        try {
            action.run();
        } catch (Throwable ex) {
            failure.set(ex);
            ex.printStackTrace();
            finish();
        }
    }

    private static void finish() {
        try {
            Files.write(directory.resolve("manifest.tsv"), rows);
            Files.write(directory.resolve("resources.tsv"), resources);
            if (app != null) app.stop();
            if (stage != null) stage.close();
        } catch (Exception ex) {
            failure.compareAndSet(null, ex);
        } finally {
            Platform.exit();
            finished.countDown();
        }
    }
}
