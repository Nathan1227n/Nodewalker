package io.github.nathan1227n.nodewalker.app.world.render;

import io.github.nathan1227n.nodewalker.app.world.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import javafx.geometry.Point3D;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;

/** All methods, including construction/disposal, are called by TspWorldView on the FX thread. */
final class World3DRenderer extends Region implements WorldRenderer {
    private final WorldViewState state;
    private final IntConsumer selection;
    private final Group world = new Group();
    private final Group secondaryEdges = new Group();
    private final Group routeGeometry = new Group();
    private final Group selectionRing = new Group();
    private final Pane labels = new Pane();
    private final Map<Integer, Label> refugeLabels = new HashMap<>();
    private final Map<Integer, Line> leaderLines = new HashMap<>();
    private final Map<String, PhongMaterial> materials = new HashMap<>();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;
    private double dragX, dragY;
    private static final String[] ROCK = {"#243337", "#304044", "#3d4d4d", "#50605b", "#637168", "#849087"};

    World3DRenderer(WorldViewState state, IntConsumer selection) {
        this.state = state; this.selection = selection;
        buildTerrain();
        for (var refuge : state.model().refuges()) buildRefuge(refuge);
        buildRoute(); routeGeometry.setMouseTransparent(true); world.getChildren().add(routeGeometry);
        if (state.decoration()) buildDecoration();
        secondaryEdges.setMouseTransparent(true); selectionRing.setMouseTransparent(true);
        world.getChildren().addAll(secondaryEdges, selectionRing);
        selectionRing.getChildren().add(ring(Vec3.ZERO, 23, "#eef8db"));

        var ambient = new AmbientLight(Color.web("#738b9c", .66));
        var key = new DirectionalLight(Color.web("#d4e4ef"));
        key.setDirection(new Point3D(.7, 1, .55));
        var rim = new DirectionalLight(Color.web("#657e8a"));
        rim.setDirection(new Point3D(-1, .2, -.6));
        var root = new Group(world, ambient, key, rim);
        subScene = new SubScene(root, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);
        camera.setFieldOfView(38); camera.setVerticalFieldOfView(true);
        subScene.setCamera(camera);
        labels.setPickOnBounds(false);
        getChildren().addAll(subScene, labels);
        setStyle("-fx-background-color: linear-gradient(to bottom, #0c1622 0%, #263a46 58%, #101c24 100%);");
        // The viewport also receives drags over empty sky and label overlays, not just picked 3D shapes.
        setPickOnBounds(true);
        setOnMousePressed(e -> { dragX = e.getSceneX(); dragY = e.getSceneY(); });
        setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                state.camera().orbit(e.getSceneX() - dragX, e.getSceneY() - dragY);
                dragX = e.getSceneX(); dragY = e.getSceneY(); updateCamera();
            }
        });
        setOnScroll(e -> { state.camera().zoom(e.getDeltaY()); updateCamera(); e.consume(); });
        refresh();
    }

    private void buildTerrain() {
        TerrainData terrain = state.model().terrain(state.simplified());
        float[] points = new float[terrain.vertices().size() * 3];
        for (int i = 0; i < terrain.vertices().size(); i++) {
            Vec3 p = terrain.vertices().get(i);
            points[i * 3] = (float) p.x(); points[i * 3 + 1] = (float) p.y(); points[i * 3 + 2] = (float) p.z();
        }
        for (int band = 0; band < ROCK.length; band++) {
            final int selectedBand = band;
            var faces = terrain.faces().stream().filter(f -> f.material() == selectedBand).toList();
            int[] indices = new int[faces.size() * 6];
            for (int i = 0; i < faces.size(); i++) {
                var f = faces.get(i);
                indices[i * 6] = f.a(); indices[i * 6 + 2] = f.b(); indices[i * 6 + 4] = f.c();
            }
            var mesh = new TriangleMesh(); mesh.getPoints().setAll(points); mesh.getTexCoords().setAll(0, 0);
            mesh.getFaces().setAll(indices);
            // Faceted rock, with smaller faces than the spike. Deliberate edges retain the ridges.
            mesh.getFaceSmoothingGroups().setAll(new int[faces.size()]);
            var view = new MeshView(mesh); view.setMaterial(material(ROCK[band], false));
            world.getChildren().add(view);
        }
        // A dark geological cut anchors the landscape, instead of an infinitely thin test plane.
        var skirtPoints = new ArrayList<Vec3>();
        var skirtFaces = new ArrayList<Integer>();
        double extent = terrain.extent();
        double bottom = terrain.vertices().stream().mapToDouble(Vec3::y).max().orElse(0) + 95;
        List<Vec3> anchors = state.model().positions();
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i < 40; i++) {
                double t = -extent + 2 * extent * i / 40, next = -extent + 2 * extent * (i + 1) / 40;
                Vec3 a = edgePoint(side, t, extent, anchors), b = edgePoint(side, next, extent, anchors);
                int index = skirtPoints.size();
                skirtPoints.addAll(List.of(a, b, new Vec3(b.x(), bottom, b.z()), new Vec3(a.x(), bottom, a.z())));
                for (int value : new int[]{index, index + 1, index + 2, index, index + 2, index + 3}) skirtFaces.add(value);
            }
        }
        world.getChildren().add(mesh(skirtPoints, skirtFaces, "#18272c", true));
    }

    private Vec3 edgePoint(int side, double t, double extent, List<Vec3> anchors) {
        double x = side == 0 ? -extent : side == 1 ? extent : t;
        double z = side == 2 ? -extent : side == 3 ? extent : t;
        return new Vec3(x, -TerrainFactory.heightAt(anchors, x, z), z);
    }

    private void buildRefuge(TspSceneModel.Refuge refuge) {
        int index = refuge.index(); boolean base = index == 0;
        Vec3 p = refuge.position(); String beacon = base ? "#ffbe64" : "#58dae5";
        var group = new Group();
        double width = base ? 78 : 51, depth = base ? 58 : 40;
        group.getChildren().add(box(p.add(new Vec3(0, 46, 0)), width, 6, depth, "#34474b"));
        group.getChildren().add(box(p.add(new Vec3(0, 49.5, 0)), width + 5, 2, depth + 5, "#182b32"));
        double ground = -TerrainFactory.heightAt(state.model().positions(), p.x(), p.z());
        for (double x : new double[]{-width * .39, width * .39}) {
            for (double z : new double[]{-depth * .35, depth * .35}) {
                double bottom = Math.max(p.y() + 53, ground + 4);
                group.getChildren().add(box(new Vec3(p.x() + x, (p.y() + 49 + bottom) / 2, p.z() + z),
                        4, bottom - p.y() - 49, 4, "#273b40"));
            }
        }
        if (base) {
            group.getChildren().add(box(p.add(new Vec3(-10, 30, 4)), 47, 26, 35, "#768078"));
            group.getChildren().add(box(p.add(new Vec3(25, 34, 3)), 21, 18, 38, "#465c60"));
            group.getChildren().add(box(p.add(new Vec3(-10, 15, 4)), 51, 5, 39, "#253d44"));
            group.getChildren().add(box(p.add(new Vec3(-10, 29, -14)), 38, 6, 1, beacon, true));
            group.getChildren().add(box(p.add(new Vec3(25, 27, -17)), 14, 3, 1, "#96c5bd", true));
            // Radio arch, asymmetrical service wing and a legible warm terminal silhouette.
            for (int sign : new int[]{-1, 1}) group.getChildren().add(box(p.add(new Vec3(sign * 34, 16, 17)), 3, 55, 3, "#728583"));
            group.getChildren().add(box(p.add(new Vec3(0, -11, 17)), 71, 3, 4, "#adb5a3"));
            group.getChildren().add(box(p.add(new Vec3(0, -13, 17)), 18, 1.5, 4, beacon, true));
        } else {
            group.getChildren().add(box(p.add(new Vec3(0, 32, 2)), 34, 23, 27, index % 2 == 0 ? "#63746d" : "#4d6265"));
            if (index % 3 == 0) {
                var roof = box(p.add(new Vec3(0, 18, 2)), 42, 5, 32, "#263f45"); roof.setRotationAxis(Rotate.Z_AXIS); roof.setRotate(-9);
                group.getChildren().add(roof);
            } else if (index % 3 == 1) {
                group.getChildren().add(box(p.add(new Vec3(12, 17, 3)), 9, 16, 20, "#7e8980"));
                group.getChildren().add(box(p.add(new Vec3(-10, 18, 2)), 20, 3, 30, "#263d44"));
            } else {
                group.getChildren().add(box(p.add(new Vec3(0, 19, 2)), 39, 4, 32, "#263d44"));
                group.getChildren().add(box(p.add(new Vec3(-14, 31, -14)), 4, 23, 3, "#99a092"));
            }
            group.getChildren().add(box(p.add(new Vec3(1, 29, -12)), 22, 4, 1, beacon, true));
        }
        group.getChildren().add(tube(new SegmentGeometry(p, p.add(new Vec3(0, 19, 0))), 1.5, "#a8b4a4", false));
        group.getChildren().add(sphere(p, base ? 5 : 3.5, beacon, true));
        group.getChildren().add(tube(new SegmentGeometry(p.add(new Vec3(0, -5, 0)), p.add(new Vec3(0, -43, 0))), .9, beacon, true));
        group.getChildren().add(ring(p.add(new Vec3(0, -4, 0)), base ? 9 : 6, beacon));
        if (!state.simplified()) {
            for (int rib = -1; rib <= 1; rib++) group.getChildren().add(box(p.add(new Vec3(rib * 9, 40, -depth * .4)), 1, 7, 2, "#b5b5a0"));
            group.getChildren().add(box(p.add(new Vec3(-width * .32, 42, depth * .32)), 9, 7, 8, "#7e897c"));
        }
        group.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                selection.accept(index);
                if (e.getClickCount() == 2) { state.camera().focus(p); updateCamera(); }
                e.consume();
            }
        });
        world.getChildren().add(group);
        int stride = Math.max(1, (state.model().refuges().size() + 47) / 48);
        if (index % stride == 0 || index == state.selected()) {
            addLabel(index);
        }
    }

    private void addLabel(int index) {
        boolean base = index == 0;
        var label = new Label(base ? "00 / BASE" : String.format("%02d / REFÚGIO", index));
        label.getStyleClass().addAll("world-label", base ? "base-label" : "refuge-label");
        label.setOnMouseClicked(e -> { selection.accept(index); e.consume(); });
        var leader = new Line(); leader.setStroke(Color.web(base ? "#e2bd81" : "#80a8ad", .55));
        leader.setStrokeWidth(.7); leader.setMouseTransparent(true);
        labels.getChildren().addAll(leader, label); refugeLabels.put(index, label); leaderLines.put(index, leader);
    }

    private void buildRoute() {
        boolean certified = state.model().certified();
        String color = certified ? "#a1fff1" : "#ffbf69";
        for (var edge : state.model().segments()) {
            SegmentGeometry segment = edge.geometry();
            if (segment.length() == 0) { routeGeometry.getChildren().add(sphere(segment.start(), 5, color, true)); continue; }
            var halo = tube(segment, 5.5, certified ? "#359baf" : "#ac773d", true);
            halo.setOpacity(.14); routeGeometry.getChildren().add(halo);
            if (certified) routeGeometry.getChildren().add(tube(segment, 2.5, color, true));
            else {
                // Dashed visual treatment only; the immutable segment still has the exact original endpoints.
                Vec3 delta = segment.end().subtract(segment.start());
                for (int dash = 0; dash < 10; dash++) routeGeometry.getChildren().add(tube(new SegmentGeometry(
                        segment.start().add(delta.multiply(dash / 10.0)),
                        segment.start().add(delta.multiply((dash + .62) / 10))), 2.5, color, true));
            }
        }
    }

    private void buildDecoration() {
        double extent = state.model().terrain(false).extent();
        for (int i = 0; i < (state.simplified() ? 7 : 28); i++) {
            double x = Math.sin(i * 7.13) * extent * .88, z = Math.cos(i * 4.91) * extent * .86;
            if (state.model().positions().stream().anyMatch(p -> Math.hypot(x - p.x(), z - p.z()) < 75)) continue;
            double y = -TerrainFactory.heightAt(state.model().positions(), x, z);
            var rock = box(new Vec3(x, y - 5, z), 16 + i % 4 * 8, 13 + i % 5 * 7, 13 + i % 3 * 8, "#394b4b");
            rock.setRotationAxis(new Point3D(1, .3, .4)); rock.setRotate(i * 23);
            world.getChildren().add(rock);
        }
        double x = -extent * .4, z = extent * .35;
        Vec3 foot = new Vec3(x, -TerrainFactory.heightAt(state.model().positions(), x, z), z);
        world.getChildren().add(box(foot.add(new Vec3(0, -11, 0)), 54, 22, 12, "#58645f"));
        world.getChildren().add(box(foot.add(new Vec3(24, -21, 15)), 8, 42, 40, "#465753"));
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2 / 3;
            Vec3 lower = foot.add(new Vec3(Math.cos(angle) * 23, -4, Math.sin(angle) * 23));
            Vec3 upper = foot.add(new Vec3(0, -138, 0));
            world.getChildren().add(tube(new SegmentGeometry(lower, upper), 1.7, "#7b8b82", false));
            world.getChildren().add(tube(new SegmentGeometry(lower, foot.add(new Vec3(0, -90, 0))), .9, "#4e6769", false));
        }
        world.getChildren().add(box(foot.add(new Vec3(0, -119, 0)), 53, 3, 4, "#92a098"));
        world.getChildren().add(box(foot.add(new Vec3(0, -141, 0)), 31, 2, 3, "#92a098"));
    }

    private PhongMaterial material(String hex, boolean luminous) {
        return materials.computeIfAbsent(hex + luminous, key -> {
            var material = new PhongMaterial(luminous ? Color.web("#070a0c") : Color.web(hex));
            material.setSpecularColor(Color.web(luminous ? "#101820" : "#263b40")); material.setSpecularPower(luminous ? 30 : 9);
            if (luminous) {
                var image = new WritableImage(1, 1); image.getPixelWriter().setColor(0, 0, Color.web(hex));
                material.setSelfIlluminationMap(image);
            }
            return material;
        });
    }
    private Box box(Vec3 p, double w, double h, double d, String color) { return box(p, w, h, d, color, false); }
    private Box box(Vec3 p, double w, double h, double d, String color, boolean luminous) {
        var shape = new Box(w, h, d); shape.setMaterial(material(color, luminous)); position(shape, p); return shape;
    }
    private Sphere sphere(Vec3 p, double radius, String color, boolean luminous) {
        var shape = new Sphere(radius, 10); shape.setMaterial(material(color, luminous)); position(shape, p); return shape;
    }
    private Cylinder tube(SegmentGeometry geometry, double radius, String color, boolean luminous) {
        var shape = new Cylinder(radius, Math.max(.0001, geometry.length()), 8);
        shape.setMaterial(material(color, luminous)); position(shape, geometry.midpoint());
        Vec3 axis = geometry.rotationAxis();
        shape.getTransforms().add(new Rotate(geometry.rotationDegrees(), new Point3D(axis.x(), axis.y(), axis.z())));
        return shape;
    }
    private MeshView ring(Vec3 p, double radius, String color) {
        var vertices = new ArrayList<Vec3>(); var indices = new ArrayList<Integer>();
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
            int at = vertices.size();
            for (double[] v : new double[][]{{a, radius}, {a, radius - 1.3}, {b, radius - 1.3}, {b, radius}})
                vertices.add(p.add(new Vec3(Math.cos(v[0]) * v[1], 0, Math.sin(v[0]) * v[1])));
            for (int v : new int[]{at, at + 1, at + 2, at, at + 2, at + 3}) indices.add(v);
        }
        var view = mesh(vertices, indices, color, true); view.setMaterial(material(color, true)); return view;
    }
    private MeshView mesh(List<Vec3> vertices, List<Integer> faces, String color, boolean doubleSided) {
        float[] points = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            Vec3 p = vertices.get(i); points[i * 3] = (float) p.x(); points[i * 3 + 1] = (float) p.y(); points[i * 3 + 2] = (float) p.z();
        }
        int[] indices = new int[faces.size() * 2];
        for (int i = 0; i < faces.size(); i++) indices[i * 2] = faces.get(i);
        var mesh = new TriangleMesh(); mesh.getPoints().setAll(points); mesh.getTexCoords().setAll(0, 0); mesh.getFaces().setAll(indices);
        var view = new MeshView(mesh); view.setMaterial(material(color, false));
        if (doubleSided) view.setCullFace(CullFace.NONE);
        return view;
    }
    private static void position(Node node, Vec3 p) { node.setTranslateX(p.x()); node.setTranslateY(p.y()); node.setTranslateZ(p.z()); }

    @Override protected void layoutChildren() {
        if (getWidth() <= 1 || getHeight() <= 1) return;
        subScene.setWidth(getWidth()); subScene.setHeight(getHeight()); labels.resize(getWidth(), getHeight()); updateCamera();
    }
    private void updateCamera() {
        if (subScene == null) return;
        var pose = state.camera().pose(subScene.getWidth(), subScene.getHeight());
        Vec3 r = pose.right(), d = pose.down(), f = pose.forward(), e = pose.eye();
        camera.getTransforms().setAll(new Affine(r.x(), d.x(), f.x(), e.x(), r.y(), d.y(), f.y(), e.y(), r.z(), d.z(), f.z(), e.z()));
        camera.setNearClip(pose.nearClip()); camera.setFarClip(pose.farClip());
        List<double[]> occupied = new ArrayList<>();
        // Iterating city indices explicitly also makes label placement deterministic.
        for (var refuge : state.model().refuges()) {
            Label label = refugeLabels.get(refuge.index()); if (label == null) continue;
            Vec3 p = pose.project(refuge.position(), getWidth(), getHeight());
            label.setVisible(p.z() > pose.nearClip() && p.x() > -80 && p.x() < getWidth() + 80 && p.y() > 0 && p.y() < getHeight());
            Line leader = leaderLines.get(refuge.index()); leader.setVisible(label.isVisible());
            if (!label.isVisible()) continue;
            double w = Math.max(90, label.prefWidth(-1)), h = 25;
            double x = Math.clamp(p.x() + 10, 5, Math.max(5, getWidth() - w - 5));
            double y = Math.clamp(p.y() - 36, 38, Math.max(38, getHeight() - h - 5));
            for (double[] area : occupied) if (Math.abs(x - area[0]) < w && Math.abs(y - area[1]) < h) y = Math.max(38, area[1] - h - 2);
            label.resizeRelocate(x, y, w, h); occupied.add(new double[]{x, y});
            leader.setStartX(p.x()); leader.setStartY(p.y()); leader.setEndX(x + 2); leader.setEndY(y + h);
            label.setStyle(refuge.index() == state.selected() ? "-fx-border-color: #e9f5d8;" : "");
        }
    }
    @Override public void refresh() {
        int stride = Math.max(1, (state.model().refuges().size() + 47) / 48);
        for (int index : List.copyOf(refugeLabels.keySet())) {
            if (index % stride != 0 && index != state.selected()) {
                Label label = refugeLabels.remove(index); label.setOnMouseClicked(null);
                labels.getChildren().removeAll(label, leaderLines.remove(index));
            }
        }
        if (!refugeLabels.containsKey(state.selected())) addLabel(state.selected());
        Vec3 point = state.model().refuges().get(state.selected()).position(); position(selectionRing, point);
        secondaryEdges.getChildren().clear();
        if (state.connections()) for (var edge : state.model().secondaryConnections(state.selected())) {
            if (edge.geometry().length() > 0) secondaryEdges.getChildren().add(tube(edge.geometry(), .5, "#48676d", false));
        }
        updateCamera();
    }
    @Override public Region node() { return this; }
    @Override public void resultChanged() { routeGeometry.getChildren().clear(); buildRoute(); refresh(); }
    @Override public void close() {
        setOnMousePressed(null); setOnMouseDragged(null); setOnScroll(null);
        refugeLabels.values().forEach(label -> label.setOnMouseClicked(null));
        subScene.setCamera(null); subScene.setRoot(new Group());
        world.getChildren().clear(); labels.getChildren().clear(); refugeLabels.clear(); leaderLines.clear(); materials.clear(); getChildren().clear();
    }
}
