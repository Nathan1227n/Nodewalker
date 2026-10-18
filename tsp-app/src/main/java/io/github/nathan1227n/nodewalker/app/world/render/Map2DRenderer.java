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
import javafx.scene.text.Font;

/** Independent Canvas renderer: never creates a SubScene, camera, material or 3D shape. */
final class Map2DRenderer extends Region implements WorldRenderer {
    private final Canvas backdrop = new Canvas();
    private final Canvas overlay = new Canvas();
    private final WorldViewState state;
    private final IntConsumer selection;
    private final List<Vec3> projected = new ArrayList<>();
    private double paintedWidth = -1, paintedHeight = -1;
    private static final Color[] ROCK = {Color.web("#14232c"), Color.web("#1c3038"), Color.web("#283d43"),
            Color.web("#35494c"), Color.web("#465754"), Color.web("#65716a")};

    Map2DRenderer(WorldViewState state, IntConsumer selection) {
        this.state = state; this.selection = selection;
        getChildren().addAll(backdrop, overlay);
        overlay.setOnMouseClicked(event -> {
            List<Integer> hits = new ArrayList<>();
            for (int i = 0; i < projected.size(); i++) {
                Vec3 p = projected.get(i);
                if (Math.hypot(event.getX() - p.x(), event.getY() - p.y()) < 23) hits.add(i);
            }
            if (!hits.isEmpty()) {
                int current = hits.indexOf(state.selected());
                selection.accept(hits.get((current + 1) % hits.size()));
            }
        });
    }

    @Override protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;
        if (w != paintedWidth || h != paintedHeight) {
            backdrop.setWidth(w); backdrop.setHeight(h); overlay.setWidth(w); overlay.setHeight(h);
            paintedWidth = w; paintedHeight = h;
            paintBackdrop();
        }
        refresh();
    }

    private Vec3 project(Vec3 p) { return state.model().projection().project(p, getWidth(), getHeight(), 92); }

    private void paintBackdrop() {
        GraphicsContext g = backdrop.getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        g.setFill(Color.web("#0b171f")); g.fillRect(0, 0, w, h);
        var terrain = state.model().terrain(true);
        for (var face : terrain.faces()) {
            Vec3 a = project(terrain.vertices().get(face.a()));
            Vec3 b = project(terrain.vertices().get(face.b()));
            Vec3 c = project(terrain.vertices().get(face.c()));
            g.setFill(ROCK[face.material()]);
            g.fillPolygon(new double[]{a.x(), b.x(), c.x()}, new double[]{a.y(), b.y(), c.y()}, 3);
        }
        g.setStroke(Color.web("#99c2bf", .08)); g.setLineWidth(1);
        for (int x = 30; x < w; x += 55) g.strokeLine(x, 0, x, h);
        for (int y = 30; y < h; y += 55) g.strokeLine(0, y, w, y);
        g.setFont(Font.font("Monospaced", 11)); g.setFill(Color.web("#b3c8c9"));
        g.fillText("+Z / NORTE", w - 115, 38); g.strokeLine(w - 100, 48, w - 100, 72);
        g.fillText("+X →", w - 70, h - 25);
        if (state.decoration() && !state.simplified()) {
            g.setStroke(Color.web("#a0b4ad", .3));
            g.strokeRect(36, h - 104, 28, 36); g.strokeLine(50, h - 120, 50, h - 68);
            g.fillText("RUÍNA", 30, h - 48);
        }
    }

    @Override public void refresh() {
        if (getWidth() <= 1 || getHeight() <= 1) return;
        var g = overlay.getGraphicsContext2D();
        g.clearRect(0, 0, getWidth(), getHeight());
        projected.clear();
        state.model().positions().forEach(p -> projected.add(project(p)));
        if (state.connections()) {
            g.setStroke(Color.web("#789594", .35)); g.setLineWidth(1); g.setLineDashes(3, 7);
            for (var edge : state.model().secondaryConnections(state.selected())) line(g, edge);
        }
        boolean certified = state.model().certified();
        Color route = Color.web(certified ? "#8ff6ed" : "#ffbe64");
        g.setLineDashes(); g.setLineWidth(10); g.setStroke(Color.web(certified ? "#123f47" : "#493a24", .9));
        for (var edge : state.model().segments()) line(g, edge);
        g.setLineWidth(2.5); g.setStroke(route);
        if (!certified) g.setLineDashes(9, 6);
        for (var edge : state.model().segments()) line(g, edge);
        g.setLineDashes();
        g.setFont(Font.font("Monospaced", 12)); g.setTextBaseline(VPos.CENTER);
        int stride = Math.max(1, (state.model().refuges().size() + 47) / 48);
        for (int i = 0; i < projected.size(); i++) {
            Vec3 p = projected.get(i);
            Color color = Color.web(i == 0 ? "#ffbe64" : "#58dae5");
            if (i == state.selected()) {
                g.setStroke(Color.web("#edf6e8")); g.setLineWidth(1.5); g.strokeOval(p.x() - 17, p.y() - 17, 34, 34);
            }
            g.setFill(Color.web("#0b151c")); g.fillOval(p.x() - 11, p.y() - 11, 22, 22);
            g.setStroke(color); g.setLineWidth(i == 0 ? 3 : 1.5); g.strokeOval(p.x() - 10, p.y() - 10, 20, 20);
            g.setFill(color); g.fillOval(p.x() - 4, p.y() - 4, 8, 8);
            if (i % stride == 0 || i == state.selected()) {
                // Radial label slots make coincident XZ projections inspectable without moving their anchors.
                int coincidentBefore = 0;
                for (int j = 0; j < i; j++) if (projected.get(j).subtract(p).length() < 22) coincidentBefore++;
                double ly = p.y() - 22 - coincidentBefore * 22;
                double lx = Math.clamp(p.x() + 14, 8, Math.max(8, getWidth() - 132));
                ly = Math.clamp(ly, 58, Math.max(58, getHeight() - 22));
                String label = i == 0 ? "00 · BASE" : String.format("%02d · Y=%d", i, state.model().refuges().get(i).logical().y());
                g.setFill(Color.web("#09151d", .93)); g.fillRoundRect(lx - 5, ly - 10, 118, 20, 4, 4);
                g.setFill(color); g.fillText(label, lx, ly);
                g.setStroke(Color.web("#c2d9d8", .35)); g.setLineWidth(.7); g.strokeLine(p.x(), p.y(), lx, ly + 8);
            }
        }
        long duplicates = projected.stream().distinct().count();
        if (duplicates != projected.size()) {
            g.setFill(Color.web("#ffce88"));
            g.fillText("Projeções coincidentes: clique para alternar ou use a lista de refúgios.", 22, getHeight() - 54);
        }
    }

    private void line(GraphicsContext g, TspSceneModel.RouteSegment edge) {
        Vec3 a = projected.get(edge.from()), b = projected.get(edge.to());
        if (a.equals(b)) g.strokeOval(a.x() - 7, a.y() - 7, 14, 14);
        else g.strokeLine(a.x(), a.y(), b.x(), b.y());
    }
    @Override public Region node() { return this; }
    @Override public void close() { overlay.setOnMouseClicked(null); getChildren().clear(); projected.clear(); }
}
