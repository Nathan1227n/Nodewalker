package io.github.nathan1227n.nodewalker.app.world.render;

import io.github.nathan1227n.nodewalker.app.world.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;

/**
 * Independent Canvas renderer: never creates a SubScene, camera, material or 3D shape.
 */
final class Map2DRenderer extends Region implements WorldRenderer {
    private final Canvas backdrop = new Canvas();
    private final Canvas overlay = new Canvas();
    private final WorldViewState state;
    private final IntConsumer selection;
    private final List<Vec3> projected = new ArrayList<>();
    private double paintedWidth = -1, paintedHeight = -1;
    private boolean paintedDecoration;
    private List<LabelLayout.Placement> placements = List.of();
    private static final Color[] ROCK = java.util.stream.IntStream.range(0, 6)
            .mapToObj(i -> Color.web(ScenePalette.rock(i).color())).toArray(Color[]::new);

    Map2DRenderer(WorldViewState state, IntConsumer selection) {
        this.state = state;
        this.selection = selection;
        getChildren().addAll(backdrop, overlay);
        // A thick dashed outline must not close its own gaps with projecting square caps.
        overlay.getGraphicsContext2D().setLineCap(StrokeLineCap.BUTT);
        overlay.setOnMouseClicked(event -> {
            for (var placement : placements) {
                if (placement.bounds().contains(new LabelLayout.Rect(event.getX(), event.getY(), 0, 0))) {
                    selection.accept(placement.index());
                    return;
                }
            }
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < projected.size(); i++) {
                Vec3 p = projected.get(i);
                if (state.cityFraction(i) > 0 && Math.hypot(event.getX() - p.x(), event.getY() - p.y()) < 23) hits.add(i);
            }
            if (!hits.isEmpty()) {
                int current = hits.indexOf(state.selected());
                selection.accept(hits.get((current + 1) % hits.size()));
            }
        });
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;
        if (w != paintedWidth || h != paintedHeight) {
            backdrop.setWidth(w);
            backdrop.setHeight(h);
            overlay.setWidth(w);
            overlay.setHeight(h);
            paintedWidth = w;
            paintedHeight = h;
            paintBackdrop();
        }
        refresh();
    }

    private Vec3 project(Vec3 p) {
        return state.model().projection().project(p, getWidth(), getHeight(), 92);
    }

    private void paintBackdrop() {
        GraphicsContext g = backdrop.getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        paintedDecoration = state.decoration();
        g.setFill(new ImagePattern(WorldTextures.rock(), 0, 0, 220, 220, false));
        g.fillRect(0, 0, w, h);
        g.setFill(Color.web("#08131d", .5));
        g.fillRect(0, 0, w, h);
        var terrain = state.model().terrain(true);
        for (var face : terrain.faces()) {
            Vec3 a = project(terrain.vertices().get(face.a()));
            Vec3 b = project(terrain.vertices().get(face.b()));
            Vec3 c = project(terrain.vertices().get(face.c()));
            g.setFill(ROCK[face.material()].deriveColor(0, .6, .65, .18));
            g.fillPolygon(new double[]{a.x(), b.x(), c.x()}, new double[]{a.y(), b.y(), c.y()}, 3);
        }
        if (state.decoration()) for (var plant : state.model().geometry(state.simplified()).vegetation().plants()) {
            Vec3 p = project(plant.position());
            boolean tree = plant.kind() == VegetationFactory.Kind.TREE || plant.kind() == VegetationFactory.Kind.DEAD_TREE;
            double radius = tree ? 4 : plant.kind() == VegetationFactory.Kind.SHRUB ? 2.5 : 1.5;
            g.setFill(Color.web(plant.kind() == VegetationFactory.Kind.DEAD_TREE ? ScenePalette.BARK.color() : ScenePalette.LEAVES.color(), .65));
            g.fillOval(p.x() - radius, p.y() - radius, radius * 2, radius * 2);
        }
        g.setStroke(Color.web("#99c2bf", .08));
        g.setLineWidth(1);
        for (int x = 30; x < w; x += 55) g.strokeLine(x, 0, x, h);
        for (int y = 30; y < h; y += 55) g.strokeLine(0, y, w, y);
        g.setFont(Font.font("Monospaced", 11));
        g.setFill(Color.web("#b3c8c9"));
        g.fillText("+Z / NORTE", w - 115, 38);
        g.strokeLine(w - 100, 48, w - 100, 72);
        g.fillText("+X →", w - 70, h - 25);
        if (state.decoration() && !state.simplified()) {
            g.setStroke(Color.web("#a0b4ad", .3));
            g.strokeRect(36, h - 104, 28, 36);
            g.strokeLine(50, h - 120, 50, h - 68);
            g.fillText("RUÍNA", 30, h - 48);
        }
    }

    @Override
    public void refresh() {
        if (getWidth() <= 1 || getHeight() <= 1) return;
        if (paintedDecoration != state.decoration()) paintBackdrop();
        var g = overlay.getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        projected.clear();
        state.model().positions().forEach(p -> projected.add(project(p)));
        if (state.connections()) {
            g.setStroke(Color.web("#789594", .35));
            g.setLineWidth(1);
            g.setLineDashes(3, 7);
            for (var edge : state.model().secondaryConnections(state.selected())) line(g, edge);
        }
        boolean certified = state.model().solidPath();
        Color route = Color.web(certified ? "#8ff6ed" : "#ffbe64");
        if (certified) g.setLineDashes();
        else g.setLineDashes(9, 6);
        g.setLineWidth(10);
        g.setStroke(Color.web(certified ? "#123f47" : "#493a24", .9));
        for (int i = 0; i < state.model().segments().size(); i++) line(g, state.model().segments().get(i), state.edgeFraction(i));
        g.setLineWidth(2.5);
        g.setStroke(route);
        for (int i = 0; i < state.model().segments().size(); i++) line(g, state.model().segments().get(i), state.edgeFraction(i));
        g.setLineDashes();
        g.setFont(Font.font("Monospaced", 12));
        g.setTextBaseline(VPos.CENTER);
        int stride = Math.max(1, (state.model().refuges().size() + 47) / 48);
        var requests = new ArrayList<LabelLayout.Request>();
        for (int i = 0; i < projected.size(); i++) {
            Vec3 p = projected.get(i);
            double fraction = state.cityFraction(i);
            if (fraction == 0) continue;
            Color color = Color.web(i == 0 ? "#ffbe64" : "#58dae5");
            if (i == state.selected()) {
                g.setStroke(Color.web("#edf6e8"));
                g.setLineWidth(1.5);
                g.strokeOval(p.x() - 17, p.y() - 17, 34, 34);
            }
            g.setFill(Color.web("#0b151c"));
            g.fillOval(p.x() - 11, p.y() - 11, 22, 22);
            g.setStroke(color);
            g.setLineWidth(i == 0 ? 3 : 1.5);
            g.strokeOval(p.x() - 10, p.y() - 10, 20, 20);
            g.setFill(color);
            g.fillOval(p.x() - 4 * fraction, p.y() - 4 * fraction, 8 * fraction, 8 * fraction);
            if (i % stride == 0 || i == state.selected() || state.model().priorityVertex(i)) {
                requests.add(new LabelLayout.Request(i, p, state.model().priorityVertex(i) ? 150 : i == state.selected() ? 134 : 34, 24, true));
            }
        }
        placements = LabelLayout.arrange(requests, state.selected(),
                new LabelLayout.Rect(8, 68, Math.max(0, getWidth() - 16), Math.max(0, getHeight() - 132)), state.reducedLabels(), state.model().labelPriorities());
        for (var placement : placements) {
            int index = placement.index();
            var area = placement.bounds();
            Vec3 p = placement.anchor();
            g.setStroke(Color.web("#acb8a8", .5));
            g.setLineWidth(.7);
            g.strokeLine(p.x(), p.y(), area.x() + area.width() / 2, area.y() + area.height());
            g.setFill(Color.web("#0b161b", .96));
            g.fillRoundRect(area.x(), area.y(), area.width(), area.height(), 3, 3);
            g.setFill(Color.web(index == 0 ? "#ffbe64" : "#94d5d3"));
            String tag = state.model().vertexTag(index).trim();
            String text = !tag.isEmpty() ? (tag + (index == 0 ? "" : " " + index)) : index == state.selected() ? String.format("%02d · Y=%d", index, state.model().refuges().get(index).logical().y()) : String.format("%02d", index);
            g.fillText(text, area.x() + 6, area.y() + 12);
        }
        state.playbackCursor().ifPresent(at -> {
            Vec3 p = project(at);
            g.setFill(Color.web("#e5fff6")); g.fillOval(p.x() - 5, p.y() - 5, 10, 10);
            g.setStroke(Color.web("#8ff6ed", .7)); g.setLineWidth(1.5); g.strokeOval(p.x() - 10, p.y() - 10, 20, 20);
        });
        long duplicates = projected.stream().distinct().count();
        if (duplicates != projected.size()) {
            g.setFill(Color.web("#ffce88"));
            g.fillText("Projeções coincidentes: clique para alternar ou use a lista de refúgios.", 22, getHeight() - 54);
        }
    }

    private void line(GraphicsContext g, WorldSceneModel.Edge edge) {
        if (state.cityFraction(edge.from()) == 0 || state.cityFraction(edge.to()) == 0) return;
        line(g, edge, 1);
    }

    private void line(GraphicsContext g, WorldSceneModel.Edge edge, double fraction) {
        if (fraction <= 0) return;
        Vec3 a = projected.get(edge.from()), end = projected.get(edge.to());
        Vec3 b = new SegmentGeometry(a, end).pointAt(fraction);
        if (a.equals(b)) g.strokeOval(a.x() - 7, a.y() - 7, 14, 14);
        else g.strokeLine(a.x(), a.y(), b.x(), b.y());
    }

    @Override
    public Region node() {
        return this;
    }

    @Override
    public void close() {
        overlay.setOnMouseClicked(null);
        getChildren().clear();
        projected.clear();
        placements = List.of();
    }
}
