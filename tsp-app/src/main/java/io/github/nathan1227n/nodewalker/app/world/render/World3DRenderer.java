package io.github.nathan1227n.nodewalker.app.world.render;

import io.github.nathan1227n.nodewalker.app.world.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import javafx.animation.AnimationTimer;
import java.util.EnumMap;
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

/**
 * FX-thread adapter for prepared immutable geometry. No assets, timers or work performed per idle frame.
 */
final class World3DRenderer extends Region implements WorldRenderer {
    private final WorldViewState state;
    private final IntConsumer selection;
    private final Group world = new Group(), secondaryEdges = new Group(), routeGeometry = new Group();
    private final Group selectionRing = new Group(), decoration = new Group();
    private final Pane labels = new Pane();
    private final Map<Integer, Label> refugeLabels = new HashMap<>();
    private final Map<Integer, Line> leaderLines = new HashMap<>();
    private final EnumMap<ScenePalette, PhongMaterial> materials = new EnumMap<>(ScenePalette.class);
    private final List<Stroke> routeStrokes = new ArrayList<>();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final SubScene subScene;
    private PhongMaterial terrainMaterial;
    private double dragX, dragY;
    private final ArrayDeque<Runnable> installation = new ArrayDeque<>();
    private final AnimationTimer installer = new AnimationTimer() {
        @Override public void handle(long now) {
            long started = System.nanoTime();
            do { var next = installation.poll(); if (next != null) next.run(); }
            while (!installation.isEmpty() && System.nanoTime() - started < 2_000_000);
            if (installation.isEmpty()) { stop(); refresh(); }
        }
    };
    private WorldSceneModel secondaryModel;
    private int secondarySelection = -1;
    private long routeRevision, secondaryRevision;

    private record Stroke(Cylinder shape, SegmentGeometry segment) {
    }

    World3DRenderer(WorldViewState state, IntConsumer selection) {
        this.state = state;
        this.selection = selection;
        WorldGeometry geometry = state.model().geometry(state.simplified());
        buildTerrain(geometry.horizon());
        buildTerrain(geometry.terrain());
        for (var refuge : geometry.refuges()) installation.add(() -> buildRefuge(refuge));
        for (var mesh : geometry.decoration().meshes()) installation.add(() -> decoration.getChildren().add(mesh(mesh)));
        for (var mesh : geometry.vegetation().meshes()) installation.add(() -> decoration.getChildren().add(mesh(mesh)));
        decoration.setMouseTransparent(true);
        buildRoute();
        routeGeometry.setMouseTransparent(true);
        secondaryEdges.setMouseTransparent(true);
        selectionRing.setMouseTransparent(true);
        selectionRing.getChildren().add(ring(19, ScenePalette.SELECTED));
        world.getChildren().addAll(decoration, routeGeometry, secondaryEdges, selectionRing);
        var ambient = new AmbientLight(Color.web("#657580"));
        var key = new DirectionalLight(Color.web("#b4c0c8"));
        key.setDirection(new Point3D(.6, 1, -.65));
        var rim = new DirectionalLight(Color.web("#354b60"));
        rim.setDirection(new Point3D(-1, .25, -.5));
        subScene = new SubScene(new Group(world, ambient, key, rim), 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);
        camera.setFieldOfView(38);
        camera.setVerticalFieldOfView(true);
        subScene.setCamera(camera);
        labels.setPickOnBounds(false);
        getChildren().addAll(subScene, labels);
        setStyle("-fx-background-color: linear-gradient(to bottom, #050910 0%, #1b2935 60%, #101b25 100%);");
        setPickOnBounds(true);
        setOnMousePressed(e -> {
            dragX = e.getSceneX();
            dragY = e.getSceneY();
        });
        setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                state.camera().orbit(e.getSceneX() - dragX, e.getSceneY() - dragY);
                dragX = e.getSceneX();
                dragY = e.getSceneY();
                updateCamera();
            }
        });
        setOnScroll(e -> {
            state.camera().zoom(e.getDeltaY());
            updateCamera();
            e.consume();
        });
        refresh();
        installer.start();
    }

    private void buildTerrain(TerrainData terrain) {
        var prepared = state.model().preparedWorld().buffers(terrain);
        var mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        mesh.getPoints().setAll(prepared.points());
        mesh.getNormals().setAll(prepared.normals());
        mesh.getTexCoords().setAll(prepared.uv());
        mesh.getFaces().setAll(prepared.faces());
        var view = new MeshView(mesh);
        view.setMaterial(terrainMaterial());
        world.getChildren().add(view);
    }

    private PhongMaterial terrainMaterial() {
        if (terrainMaterial != null) return terrainMaterial;
        terrainMaterial = new PhongMaterial(Color.web("#bac5ce"));
        terrainMaterial.setDiffuseMap(WorldTextures.rock());
        // Basalt is matte: wide interpolated specular streaks made the former surface look melted.
        terrainMaterial.setSpecularColor(Color.BLACK);
        return terrainMaterial;
    }

    private MeshView mesh(GeometryMesh data) { return mesh(data, false); }

    private MeshView mesh(GeometryMesh data, boolean constantOverlay) {
        var prepared = constantOverlay ? MeshBuffers.of(data, () -> { }) : state.model().preparedWorld().buffers(data);
        var mesh = new TriangleMesh();
        mesh.getPoints().setAll(prepared.points());
        mesh.getTexCoords().setAll(prepared.uv());
        mesh.getFaces().setAll(prepared.faces());
        int[] smoothing=new int[data.triangles().size()];
        if(data.material()==ScenePalette.NEEDLES||data.material()==ScenePalette.LEAVES)java.util.Arrays.fill(smoothing,1);
        mesh.getFaceSmoothingGroups().setAll(smoothing);
        var view = new MeshView(mesh);
        view.setMaterial(material(data.material()));
        // Thin panels and authored dishes intentionally expose both sides.
        view.setCullFace(CullFace.NONE);
        return view;
    }

    private void buildRefuge(RefugeGeometryFactory.RefugeGeometry refuge) {
        var group = new Group();
        refuge.meshes().forEach(m -> group.getChildren().add(mesh(m)));
        group.getChildren().add(sphere(refuge.anchor(), refuge.index() == 0 ? 4.5 : 3.3, refuge.index() == 0 ? ScenePalette.AMBER : ScenePalette.CYAN));
        group.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                selection.accept(refuge.index());
                if (e.getClickCount() == 2) {
                    state.camera().focus(refuge.anchor());
                    updateCamera();
                }
                e.consume();
            }
        });
        world.getChildren().add(group);
    }

    private void addLabel(int index) {
        var label = new Label();
        label.setUserData(index);
        label.getStyleClass().addAll("world-label", index == 0 ? "base-label" : "refuge-label");
        label.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) selection.accept(index);
            e.consume();
        });
        var leader = new Line();
        leader.setStroke(Color.web(index == 0 ? "#c9ad7a" : "#8d9d96", .6));
        leader.setStrokeWidth(.7);
        leader.setMouseTransparent(true);
        labels.getChildren().addAll(leader, label);
        refugeLabels.put(index, label);
        leaderLines.put(index, leader);
    }

    private void buildRoute() {
        boolean certified = state.model().solidPath();
        ScenePalette color = certified ? ScenePalette.PROVEN : ScenePalette.AMBER;
        long current = ++routeRevision;
        for (var edge : state.model().segments()) installation.add(() -> {
            if (current != routeRevision) return;
            SegmentGeometry segment = edge.geometry();
            if (segment.length() == 0) {
                routeGeometry.getChildren().add(sphere(segment.start(), 4, color));
                return;
            }
            if (certified) addStroke(segment, color);
            else {
                // No continuous halo bridges the gaps: partial routes really remain dashed.
                Vec3 delta = segment.end().subtract(segment.start());
                for (int dash = 0; dash < 9; dash++)
                    addStroke(new SegmentGeometry(segment.start().add(delta.multiply(dash / 9.0)),
                            segment.start().add(delta.multiply((dash + .58) / 9))), color);
            }
        });
    }

    private void addStroke(SegmentGeometry segment, ScenePalette color) {
        Cylinder tube = tube(segment, 2, color);
        routeGeometry.getChildren().add(tube);
        routeStrokes.add(new Stroke(tube, segment));
    }

    private PhongMaterial material(ScenePalette palette) {
        return materials.computeIfAbsent(palette, key -> {
            var value = new PhongMaterial(Color.web(key.luminous() ? "#050707" : key.color()));
            value.setSpecularColor(Color.web(key.luminous() ? "#101613" : "#202622"));
            value.setSpecularPower(key.luminous() ? 20 : 8);
            if (key == ScenePalette.CONCRETE || key.ordinal() <= ScenePalette.ROCK_PEAK.ordinal()) {
                value.setDiffuseMap(WorldTextures.rock());
                value.setDiffuseColor(Color.web(key == ScenePalette.CONCRETE ? "#c0c5ba" : "#9ca7ac"));
                value.setSpecularColor(Color.BLACK);
            }
            if(key==ScenePalette.BARK||key==ScenePalette.NEEDLES||key==ScenePalette.LEAVES||key==ScenePalette.GRASS||key==ScenePalette.MOSS)
                value.setSpecularColor(Color.BLACK);
            if(key==ScenePalette.NEEDLES||key==ScenePalette.LEAVES||key==ScenePalette.MOSS) {
                value.setDiffuseMap(WorldTextures.foliage());
                value.setDiffuseColor(Color.web(key==ScenePalette.NEEDLES?"#aab9a4":"#d7dbba"));
            }
            if (key.luminous()) {
                var image = new WritableImage(1, 1);
                image.getPixelWriter().setColor(0, 0, Color.web(key.color()));
                value.setSelfIlluminationMap(image);
            }
            return value;
        });
    }

    private Sphere sphere(Vec3 p, double radius, ScenePalette color) {
        var shape = new Sphere(radius, 10);
        shape.setMaterial(material(color));
        position(shape, p);
        return shape;
    }

    private Cylinder tube(SegmentGeometry segment, double radius, ScenePalette color) {
        var shape = new Cylinder(radius, Math.max(.0001, segment.length()), 8);
        shape.setMaterial(material(color));
        position(shape, segment.midpoint());
        Vec3 axis = segment.rotationAxis();
        shape.getTransforms().add(new Rotate(segment.rotationDegrees(), new Point3D(axis.x(), axis.y(), axis.z())));
        return shape;
    }

    private MeshView ring(double radius, ScenePalette color) {
        var vertices = new ArrayList<Vec3>();
        var faces = new ArrayList<GeometryMesh.Triangle>();
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
            int at = vertices.size();
            vertices.add(new Vec3(Math.cos(a) * radius, 0, Math.sin(a) * radius));
            vertices.add(new Vec3(Math.cos(a) * (radius - 1), 0, Math.sin(a) * (radius - 1)));
            vertices.add(new Vec3(Math.cos(b) * (radius - 1), 0, Math.sin(b) * (radius - 1)));
            vertices.add(new Vec3(Math.cos(b) * radius, 0, Math.sin(b) * radius));
            faces.add(new GeometryMesh.Triangle(at, at + 1, at + 2));
            faces.add(new GeometryMesh.Triangle(at, at + 2, at + 3));
        }
        return mesh(new GeometryMesh(color, vertices, faces), true);
    }

    private static void position(Node node, Vec3 p) {
        node.setTranslateX(p.x());
        node.setTranslateY(p.y());
        node.setTranslateZ(p.z());
    }

    @Override
    protected void layoutChildren() {
        if (getWidth() <= 1 || getHeight() <= 1) return;
        subScene.setWidth(getWidth());
        subScene.setHeight(getHeight());
        labels.resize(getWidth(), getHeight());
        updateCamera();
    }

    private void updateCamera() {
        if (subScene == null || getWidth() <= 1 || getHeight() <= 1) return;
        var pose = state.camera().pose(subScene.getWidth(), subScene.getHeight());
        Vec3 r = pose.right(), d = pose.down(), f = pose.forward(), e = pose.eye();
        camera.getTransforms().setAll(new Affine(r.x(), d.x(), f.x(), e.x(), r.y(), d.y(), f.y(), e.y(), r.z(), d.z(), f.z(), e.z()));
        camera.setNearClip(pose.nearClip());
        camera.setFarClip(pose.farClip());
        double focal = getHeight() / (2 * Math.tan(Math.toRadians(19)));
        for (Stroke stroke : routeStrokes) {
            double depth = stroke.segment.midpoint().subtract(pose.eye()).dot(pose.forward());
            stroke.shape.setRadius(Math.clamp(depth * 1.05 / focal, .35, 5));
        }
        var requests = new ArrayList<LabelLayout.Request>();
        for (var refuge : state.model().refuges()) {
            Label label = refugeLabels.get(refuge.index());
            if (label == null) continue;
            boolean selected = refuge.index() == state.selected();
            String tag = state.model().vertexTag(refuge.index()).trim();
            label.setText(!tag.isEmpty() ? tag + (refuge.index() == 0 ? "" : " " + refuge.index()) : selected ? String.format("%02d / REFÚGIO", refuge.index()) : String.format("%02d", refuge.index()));
            label.setStyle(selected ? "-fx-border-color: #e5ead1;" : "");
            Vec3 point = pose.project(refuge.position(), getWidth(), getHeight());
            requests.add(new LabelLayout.Request(refuge.index(), point, state.model().priorityVertex(refuge.index()) ? 150 : selected ? 122 : 34, 24,
                    point.z() > pose.nearClip() && point.x() >= 0 && point.x() < getWidth() && point.y() >= 0 && point.y() < getHeight()));
            label.setVisible(false);
            leaderLines.get(refuge.index()).setVisible(false);
        }
        var viewport = new LabelLayout.Rect(8, 68, Math.max(0, getWidth() - 16), Math.max(0, getHeight() - 113));
        for (var placement : LabelLayout.arrange(requests, state.selected(), viewport, state.reducedLabels(), state.model().labelPriorities())) {
            var area = placement.bounds();
            Label label = refugeLabels.get(placement.index());
            label.setVisible(true);
            label.resizeRelocate(area.x(), area.y(), area.width(), area.height());
            Line line = leaderLines.get(placement.index());
            line.setVisible(true);
            line.setStartX(placement.anchor().x());
            line.setStartY(placement.anchor().y());
            line.setEndX(area.x() + area.width() / 2);
            line.setEndY(area.y() + area.height());
        }
    }

    @Override
    public void refresh() {
        int stride = Math.max(1, (state.model().refuges().size() + 47) / 48);
        for (int index : List.copyOf(refugeLabels.keySet()))
            if (index % stride != 0 && index != state.selected() && !state.model().priorityVertex(index)) {
                Label label = refugeLabels.remove(index);
                label.setOnMouseClicked(null);
                labels.getChildren().removeAll(label, leaderLines.remove(index));
            }
        for (int index = 0; index < state.model().refuges().size(); index++)
            if ((index % stride == 0 || index == state.selected() || state.model().priorityVertex(index)) && !refugeLabels.containsKey(index)) addLabel(index);
        position(selectionRing, state.model().refuges().get(state.selected()).position());
        decoration.setVisible(state.decoration());
        secondaryEdges.setVisible(state.connections());
        if (state.connections() && (secondaryModel != state.model() || secondarySelection != state.selected())) {
            secondaryModel = state.model(); secondarySelection = state.selected();
            secondaryEdges.getChildren().clear();
            long current = ++secondaryRevision;
            for (var edge : state.model().secondaryConnections(state.selected())) installation.add(() -> {
                if (current == secondaryRevision && edge.geometry().length() > 0)
                    secondaryEdges.getChildren().add(tube(edge.geometry(), .5, ScenePalette.STEEL));
            });
            installer.start();
        }
        updateCamera();
    }

    @Override
    public boolean ready() { return installation.isEmpty(); }

    @Override
    public Region node() {
        return this;
    }

    @Override
    public void resultChanged() {
        routeGeometry.getChildren().clear();
        routeStrokes.clear();
        buildRoute();
        installer.start();
        refresh();
    }

    @Override
    public void close() {
        installer.stop(); installation.clear(); secondaryModel = null;
        setOnMousePressed(null);
        setOnMouseDragged(null);
        setOnScroll(null);
        refugeLabels.values().forEach(l -> l.setOnMouseClicked(null));
        subScene.setCamera(null);
        subScene.setRoot(new Group());
        world.getChildren().clear();
        labels.getChildren().clear();
        refugeLabels.clear();
        leaderLines.clear();
        materials.clear();
        terrainMaterial = null;
        routeStrokes.clear();
        getChildren().clear();
    }
}
