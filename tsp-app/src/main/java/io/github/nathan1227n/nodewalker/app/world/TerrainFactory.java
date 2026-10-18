package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.List;

/** Authored deterministic landscape. Terrain adapts around anchors; anchors never adapt to terrain. */
public final class TerrainFactory {
    private TerrainFactory() { }

    public static double extent(List<Vec3> anchors) {
        return Math.max(760, anchors.stream().mapToDouble(p -> Math.max(Math.abs(p.x()), Math.abs(p.z())))
                .max().orElse(0) + 320);
    }

    public static TerrainData build(List<Vec3> anchors, boolean simplified) {
        int cells = simplified ? 32 : 80;
        double extent = extent(anchors);
        var vertices = new ArrayList<Vec3>((cells + 1) * (cells + 1));
        var faces = new ArrayList<TerrainData.Face>(cells * cells * 2);
        for (int z = 0; z <= cells; z++) {
            for (int x = 0; x <= cells; x++) {
                double px = -extent + 2 * extent * x / cells;
                double pz = -extent + 2 * extent * z / cells;
                vertices.add(new Vec3(px, -heightAt(anchors, px, pz), pz));
            }
        }
        for (int z = 0; z < cells; z++) {
            for (int x = 0; x < cells; x++) {
                int a = z * (cells + 1) + x, b = a + 1, c = a + cells + 1, d = c + 1;
                // CCW seen from above in JavaFX (negative Y).
                face(faces, vertices, a, b, d);
                face(faces, vertices, a, d, c);
            }
        }
        return new TerrainData(vertices, faces, extent);
    }

    private static void face(List<TerrainData.Face> faces, List<Vec3> vertices, int a, int b, int c) {
        Vec3 center = vertices.get(a).add(vertices.get(b)).add(vertices.get(c)).multiply(1.0 / 3);
        double shade = Math.sin(center.x() * .017 + center.z() * .011) * .35;
        int band = (int) Math.clamp((-center.y() + 420) / 130 + shade, 0, 5);
        faces.add(new TerrainData.Face(a, b, c, band));
    }

    public static double heightAt(List<Vec3> anchors, double x, double z) {
        double extent = extent(anchors);
        double u = x / extent, v = z / extent;
        double floor = anchors.stream().mapToDouble(p -> -p.y()).min().orElse(0) - 125;
        double height = floor + 32 * Math.sin(u * 5 + v * 2) + 20 * Math.cos(v * 9 - u * 3);
        height += peak(u, v, -.72, -.56, .19, 570) + peak(u, v, .55, -.76, .22, 660);
        height += peak(u, v, .83, .35, .24, 460) + peak(u, v, -.68, .68, .22, 510);
        height += 54 * Math.pow(Math.abs(Math.sin(u * 14 + Math.sin(v * 7))), 3)
                * Math.min(1, Math.hypot(u, v));
        // Soft shelves and a local cap keep each connection marker clear, including very low cities.
        for (Vec3 anchor : anchors) {
            double r = Math.hypot(x - anchor.x(), z - anchor.z());
            double weight = Math.exp(-r * r / (2 * 118 * 118));
            height += (-anchor.y() - 70 - height) * weight;
        }
        for (Vec3 anchor : anchors) {
            double r = Math.hypot(x - anchor.x(), z - anchor.z());
            if (r < 80) height = Math.min(height, -anchor.y() - 55 + r * .2);
        }
        return height;
    }

    private static double peak(double x, double z, double cx, double cz, double width, double height) {
        double r = Math.hypot(x - cx, z - cz) / width;
        return height * Math.exp(-r * r * .65) * (1 + .1 * Math.cos(x * 28 + z * 19));
    }
}
