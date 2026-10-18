package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Small offline-style modelling vocabulary; output batches have one material each.
 */
final class GeometryBuilder {
    private final Vec3 origin;
    private final double scale;
    private final EnumMap<ScenePalette, Batch> batches = new EnumMap<>(ScenePalette.class);

    GeometryBuilder(Vec3 origin, double scale) {
        this.origin = origin;
        this.scale = scale;
    }

    private static final class Batch {
        final List<Vec3> vertices = new ArrayList<>();
        final List<GeometryMesh.Triangle> faces = new ArrayList<>();
    }

    void polygon(ScenePalette material, Vec3... points) {
        Batch b = batches.computeIfAbsent(material, ignored -> new Batch());
        int offset = b.vertices.size();
        for (Vec3 point : points) b.vertices.add(origin.add(point.multiply(scale)));
        for (int i = 1; i < points.length - 1; i++)
            b.faces.add(new GeometryMesh.Triangle(offset, offset + i, offset + i + 1));
    }

    void box(ScenePalette material, double x, double y, double z, double w, double h, double d) {
        box(material, new Vec3(x, y, z), w, h, d, 0, 0);
    }

    void box(ScenePalette material, Vec3 center, double w, double h, double d, double yaw, double pitch) {
        Vec3[] p = new Vec3[8];
        double[][] signs = {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}, {-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}};
        double a = Math.toRadians(yaw), t = Math.toRadians(pitch);
        for (int i = 0; i < signs.length; i++) {
            double x = signs[i][0] * w / 2, y = signs[i][1] * h / 2, z = signs[i][2] * d / 2;
            double py = y * Math.cos(t) - z * Math.sin(t), pz = y * Math.sin(t) + z * Math.cos(t);
            p[i] = center.add(new Vec3(x * Math.cos(a) + pz * Math.sin(a), py, -x * Math.sin(a) + pz * Math.cos(a)));
        }
        for (int[] f : new int[][]{{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 4, 7, 3}})
            polygon(material, p[f[0]], p[f[1]], p[f[2]], p[f[3]]);
    }

    void beam(ScenePalette material, Vec3 a, Vec3 b, double radius) {
        Vec3 delta = b.subtract(a);
        if (delta.length() < 1e-9) return;
        Vec3 f = delta.unit(), u = f.cross(Math.abs(f.y()) < .8 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).unit();
        Vec3 v = f.cross(u);
        for (int i = 0; i < 6; i++) {
            double t = i * Math.PI / 3, next = (i + 1) * Math.PI / 3;
            Vec3 r = u.multiply(Math.cos(t) * radius).add(v.multiply(Math.sin(t) * radius));
            Vec3 s = u.multiply(Math.cos(next) * radius).add(v.multiply(Math.sin(next) * radius));
            polygon(material, a.add(r), a.add(s), b.add(s), b.add(r));
            polygon(material, a, a.add(s), a.add(r));
            polygon(material, b, b.add(r), b.add(s));
        }
    }

    void deck(ScenePalette material, Vec3 center, double radiusX, double radiusZ, double height) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8, b = (i + 1) * Math.PI / 4 + Math.PI / 8;
            Vec3 p = center.add(new Vec3(Math.cos(a) * radiusX, -height / 2, Math.sin(a) * radiusZ));
            Vec3 q = center.add(new Vec3(Math.cos(b) * radiusX, -height / 2, Math.sin(b) * radiusZ));
            Vec3 r = q.add(new Vec3(0, height, 0)), s = p.add(new Vec3(0, height, 0));
            polygon(material, center.add(new Vec3(0, -height / 2, 0)), p, q);
            polygon(material, p, s, r, q);
            polygon(material, center.add(new Vec3(0, height / 2, 0)), r, s);
        }
    }

    void cabin(ScenePalette material, Vec3 center, double width, double height, double depth) {
        double[][] cross = {{-width / 2, height / 2}, {width / 2, height / 2}, {width / 2, -height * .12},
                {width * .31, -height / 2}, {-width * .31, -height / 2}, {-width / 2, -height * .12}};
        Vec3[] front = new Vec3[6], back = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            front[i] = center.add(new Vec3(cross[i][0], cross[i][1], -depth / 2));
            back[i] = center.add(new Vec3(cross[i][0], cross[i][1], depth / 2));
        }
        polygon(material, front);
        polygon(material, back[5], back[4], back[3], back[2], back[1], back[0]);
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            polygon(material, front[i], back[i], back[j], front[j]);
        }
    }

    void dish(Vec3 center, double radius) {
        for (int ring = 0; ring < 3; ring++)
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6, b = (i + 1) * Math.PI / 6;
                double inner = radius * ring / 3, outer = radius * (ring + 1) / 3;
                if (ring == 0)
                    polygon(ScenePalette.METAL, center.add(new Vec3(0, 0, radius * .35)), dishPoint(center, outer, b, radius), dishPoint(center, outer, a, radius));
                else
                    polygon(ScenePalette.METAL, dishPoint(center, inner, a, radius), dishPoint(center, inner, b, radius),
                            dishPoint(center, outer, b, radius), dishPoint(center, outer, a, radius));
            }
        beam(ScenePalette.STEEL, center.add(new Vec3(0, 0, radius * .35)), center.add(new Vec3(0, 0, -radius * .6)), .6);
    }

    private Vec3 dishPoint(Vec3 c, double r, double a, double radius) {
        return c.add(new Vec3(Math.cos(a) * r, Math.sin(a) * r, radius * .35 * (1 - r * r / (radius * radius))));
    }

    void solar(Vec3 center, double width, double depth) {
        box(ScenePalette.METAL, center, width + 1.5, 1, depth + 1.5, 0, -22);
        box(ScenePalette.PANEL, center.add(new Vec3(0, -.7, 0)), width, .6, depth, 0, -22);
        for (int i = -1; i <= 1; i++)
            box(ScenePalette.STEEL,
                    center.add(new Vec3(i * width / 3, -1, 0)), .35, .5, depth, 0, -22);
        beam(ScenePalette.STEEL, center, center.add(new Vec3(0, 8, 0)), 1);
    }

    List<GeometryMesh> build() {
        return batches.entrySet().stream().map(e -> new GeometryMesh(e.getKey(), e.getValue().vertices, e.getValue().faces)).toList();
    }
}
