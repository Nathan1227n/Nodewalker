package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Authored deterministic landscape. Terrain adapts around anchors; anchors never adapt to terrain.
 */
public final class TerrainFactory {
    private TerrainFactory() {
    }

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
        double shade = Math.sin(center.x() * .013 + center.z() * .009) * .55;
        double slope = 1 - Math.abs(vertices.get(b).subtract(vertices.get(a)).cross(vertices.get(c).subtract(vertices.get(a))).unit().y());
        int band = (int) Math.clamp((-center.y() + 500) / 240 + slope * 1.5 + shade, 0, 5);
        faces.add(new TerrainData.Face(a, b, c, band));
    }

    public static double heightAt(List<Vec3> anchors, double x, double z) {
        double extent = extent(anchors);
        double u = x / extent, v = z / extent;
        double floor = anchors.stream().mapToDouble(p -> -p.y()).min().orElse(0) - 125;
        double height = floor + 52 * Math.sin(u * 3 + v) + 34 * Math.cos(v * 4 - u * 2);
        height += peak(u, v, -1.3, -.75, .46, 520) + peak(u, v, 1.05, -1.15, .5, 640);
        height += peak(u, v, 1.45, .65, .4, 540) + peak(u, v, -1.3, 1.2, .5, 520);
        height += peak(u, v, -2.55, -1.7, .65, 1020) + peak(u, v, 2.3, -2.1, .7, 1180);
        height += peak(u, v, 2.8, 1.3, .6, 940) + peak(u, v, -2.4, 2.2, .8, 930);
        height += 35 * Math.pow(Math.abs(Math.sin(u * 9 + Math.sin(v * 6))), 3) * Math.min(1, Math.hypot(u, v));
        height -= 80 * Math.exp(-Math.pow((v - .2 - .24 * Math.sin(u * 2)) / .24, 2));
        // A broad shared upland, not a narrow mountain generated underneath every city.
        double weighted = 0, total = 0;
        for (Vec3 anchor : anchors) {
            double r = Math.hypot(x - anchor.x(), z - anchor.z());
            double weight = Math.exp(-r * r / (2 * 185 * 185));
            weighted += (-anchor.y() - 65) * weight;
            total += weight;
        }
        if (total > 1e-9) height += (weighted / total - height) * Math.min(1, total);
        for (Vec3 anchor : anchors) {
            double r = Math.hypot(x - anchor.x(), z - anchor.z());
            // A continuous envelope avoids a vertical circular cliff at an arbitrary cutoff radius.
            height = Math.min(height, -anchor.y() - 55 + r * r / 180);
        }
        return height;
    }

    /**
     * Coarse static annulus, sharing the exact central boundary; no per-frame LOD or exposed skirt.
     */
    public static TerrainData horizon(List<Vec3> anchors, boolean simplified) {
        int cells = simplified ? 32 : 80, levels = simplified ? 4 : 8, perimeter = cells * 4;
        double inner = extent(anchors), outer = inner * 3.6;
        var points = new ArrayList<Vec3>();
        var faces = new ArrayList<TerrainData.Face>();
        for (int ring = 0; ring <= levels; ring++) {
            double fraction = ring / (double) levels;
            double radius = inner + (outer - inner) * fraction * fraction;
            for (int side = 0; side < 4; side++)
                for (int i = 0; i < cells; i++) {
                    double t = -radius + 2 * radius * i / cells;
                    double reverse = -radius + 2 * radius * (cells - i) / cells;
                    double x = side == 0 ? t : side == 1 ? radius : side == 2 ? reverse : -radius;
                    double z = side == 0 ? -radius : side == 1 ? t : side == 2 ? radius : reverse;
                    points.add(new Vec3(x, -heightAt(anchors, x, z), z));
                }
        }
        for (int ring = 0; ring < levels; ring++)
            for (int i = 0; i < perimeter; i++) {
                int a = ring * perimeter + i, b = ring * perimeter + (i + 1) % perimeter, c = a + perimeter, d = b + perimeter;
                int material = 6 + Math.min(2, ring * 3 / levels);
                if (ring < (simplified ? 1 : 2)) {
                    face(faces, points, a, c, d);
                    face(faces, points, a, d, b);
                } else {
                    faces.add(new TerrainData.Face(a, c, d, material));
                    faces.add(new TerrainData.Face(a, d, b, material));
                }
            }
        return new TerrainData(points, faces, outer);
    }

    private static double peak(double x, double z, double cx, double cz, double width, double height) {
        double r = Math.hypot(x - cx, z - cz) / width;
        return height * Math.exp(-r * r * .65) * (1 + .1 * Math.cos(x * 28 + z * 19));
    }
}
