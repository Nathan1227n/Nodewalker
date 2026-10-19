package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/**
 * Authored survival compounds; variants change decoration, never the logical connection anchor.
 */
public final class RefugeGeometryFactory {
    public enum Variant {CENTRAL, COMMUNICATIONS, SUPPLIES, MONITORING}

    public record RefugeGeometry(int index, Vec3 anchor, Variant variant, List<GeometryMesh> meshes) {
        public RefugeGeometry {
            meshes = List.copyOf(meshes);
        }
    }

    private RefugeGeometryFactory() {
    }

    public static RefugeGeometry build(int index, Vec3 anchor, double groundY, boolean simple) {
        Variant variant = index == 0 ? Variant.CENTRAL : Variant.values()[1 + (index - 1) % 3];
        var b = new GeometryBuilder(anchor, .8);
        boolean base = index == 0;
        ScenePalette signal = base ? ScenePalette.AMBER : ScenePalette.CYAN;
        double width = base ? 82 : 57, depth = base ? 65 : 48;
        b.deck(ScenePalette.STEEL, new Vec3(0, 49, 0), width / 2, depth / 2, 6);
        b.deck(ScenePalette.CONCRETE, new Vec3(0, 45.5, 0), width / 2 - 2, depth / 2 - 2, 2);
        double foot = Math.max(54, (groundY - anchor.y()) / .8 + 3);
        for (int sx : new int[]{-1, 1})
            for (int sz : new int[]{-1, 1}) {
                Vec3 top = new Vec3(sx * width * .31, 51, sz * depth * .3);
                b.beam(ScenePalette.STEEL, top, new Vec3(top.x(), foot, top.z()), 2);
            }
        if (base) {
            b.cabin(ScenePalette.CONCRETE, new Vec3(-12, 31, -2), 37, 26, 31);
            b.cabin(ScenePalette.STEEL, new Vec3(23, 35, 5), 25, 18, 25);
            b.box(signal, -12, 29, -18, 24, 3.2, .7);
            b.box(ScenePalette.PANEL, -12, 36, -18.5, 8, 12, .8);
            b.box(signal, 23, 32, -8, 17, 2, .7);
            tower(b, new Vec3(24, 40, 20), 68, simple);
            if (!simple) {
                b.dish(new Vec3(24, -28, 19), 9);
                b.solar(new Vec3(-29, 32, 18), 15, 22);
                generator(b, new Vec3(11, 39, -23));
                rail(b, -35, 40, -27, 35, 40, -27);
            }
        } else if (variant == Variant.COMMUNICATIONS) {
            b.cabin(ScenePalette.CONCRETE, new Vec3(-10, 32, -3), 27, 24, 27);
            b.box(signal, -10, 29, -17, 17, 2.4, .8);
            tower(b, new Vec3(15, 41, 11), 66, simple);
            if (!simple) {
                b.dish(new Vec3(15, -24, 10), 8);
                generator(b, new Vec3(-12, 40, 18));
            }
        } else if (variant == Variant.SUPPLIES) {
            b.cabin(ScenePalette.CONCRETE, new Vec3(-5, 32, 2), 35, 23, 33);
            b.box(ScenePalette.PANEL, -5, 35, -15, 16, 16, .8);
            b.box(signal, -5, 25, -15.5, 20, 2, .8);
            b.box(ScenePalette.RUST, 21, 38, 7, 10, 12, 25);
            b.box(ScenePalette.STEEL, -5, 18, -14, 42, 2, 6);
            if (!simple) {
                for (int x : new int[]{-22, 12})
                    b.beam(ScenePalette.METAL, new Vec3(x, 44, -21), new Vec3(x, 18, -21), .8);
                generator(b, new Vec3(19, 40, -15));
                b.solar(new Vec3(-14, 15, 5), 15, 20);
            }
        } else {
            b.deck(ScenePalette.CONCRETE, new Vec3(0, 32, 0), 19, 19, 24);
            b.deck(ScenePalette.STEEL, new Vec3(0, 18, 0), 22, 22, 4);
            b.box(signal, 0, 27, -18, 22, 2, .8);
            b.beam(ScenePalette.METAL, new Vec3(0, 18, 0), new Vec3(0, -22, 0), 1);
            b.box(ScenePalette.METAL, 0, -16, 0, 21, 1, 2);
            if (!simple) {
                b.solar(new Vec3(21, 31, 9), 12, 19);
                b.dish(new Vec3(-12, 8, -2), 6);
            }
        }
        if (!simple) {
            // Small ribs, vents and steps, batched rather than a Node for every component.
            for (int i = 0; i < 3; i++) b.box(ScenePalette.STEEL, -10 + i * 7, 39, -depth * .28, 1, 9, 2);
            for (int step = 0; step < 3; step++)
                b.box(ScenePalette.METAL, 0, 46 + step * 2, -depth / 2 - 2 - step * 3, 12, 2, 5);
        }
        b.beam(ScenePalette.METAL, new Vec3(0, 0, 0), new Vec3(0, 18, 0), 1.3);
        b.beam(signal, new Vec3(0, -6, 0), new Vec3(0, -45, 0), .6);
        return new RefugeGeometry(index, anchor, variant, b.build());
    }

    static void tower(GeometryBuilder b, Vec3 foot, double height, boolean simple) {
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2 / 3;
            Vec3 lower = foot.add(new Vec3(Math.cos(angle) * 8, 0, Math.sin(angle) * 8));
            Vec3 upper = foot.add(new Vec3(Math.cos(angle) * 2, -height, Math.sin(angle) * 2));
            b.beam(ScenePalette.METAL, lower, upper, .8);
            if (!simple) for (int level = 0; level < 3; level++) {
                Vec3 a = lower.add(upper.subtract(lower).multiply(level / 3.0));
                double next = (i + 1) * Math.PI * 2 / 3;
                Vec3 other = foot.add(new Vec3(Math.cos(next) * 8, 0, Math.sin(next) * 8));
                Vec3 end = foot.add(new Vec3(Math.cos(next) * 2, -height, Math.sin(next) * 2));
                b.beam(ScenePalette.STEEL, a, other.add(end.subtract(other).multiply((level + 1) / 3.0)), .45);
            }
        }
        b.box(ScenePalette.METAL, foot.x(), foot.y() - height + 3, foot.z(), 25, 1, 2);
    }

    static void generator(GeometryBuilder b, Vec3 at) {
        b.cabin(ScenePalette.RUST, at, 11, 8, 13);
        for (int i = -1; i <= 1; i++) b.box(ScenePalette.STEEL, at.x() + i * 3, at.y(), at.z() - 7, 1, 6, 1);
        b.beam(ScenePalette.METAL, at.add(new Vec3(5, 2, 3)), at.add(new Vec3(5, -7, 3)), .6);
    }

    private static void rail(GeometryBuilder b, double x, double y, double z, double tx, double ty, double tz) {
        b.beam(ScenePalette.METAL, new Vec3(x, y, z), new Vec3(tx, ty, tz), .6);
        for (int i = 0; i < 4; i++) {
            double px = x + (tx - x) * i / 3;
            b.beam(ScenePalette.METAL, new Vec3(px, y, z), new Vec3(px, y + 5, z), .5);
        }
    }
}
