package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Static authored biomes: all positions are decorative; no city, route, cost or FX object is changed.
 */
public final class VegetationFactory {
    public enum Kind {TREE, DEAD_TREE, SHRUB, GRASS, BOULDER}

    public record Plant(int id, Kind kind, Vec3 position, double height) {
    }

    public record Vegetation(List<Plant> plants, List<GeometryMesh> meshes) {
        public Vegetation {
            plants = List.copyOf(plants);
            meshes = List.copyOf(meshes);
        }
    }

    private VegetationFactory() {
    }

    public static Vegetation prepare(List<Vec3> anchors, boolean simple) {
        var plants = new ArrayList<Plant>();
        var geometry = new GeometryBuilder(Vec3.ZERO, 1);
        double extent = TerrainFactory.extent(anchors);
        double[][] regions = {{-.5, .08}, {.3, .48}, {.08, -.55}, {.62, -.13}, {-.5, -.58}, {.03, .05}, {.45, -.6}, {-.85, -.05}};
        for (int region = 0; region < regions.length; region++)
            for (int i = 0; i < 48; i++) {
                Kind kind = i < 7 ? Kind.TREE : i < 10 ? Kind.DEAD_TREE : i < 18 ? Kind.SHRUB : i < 44 ? Kind.GRASS : Kind.BOULDER;
                if (simple && i % 6 != 0) continue;
                int id = region * 48 + i;
                double angle = i * 2.399963229728653 + region * .83;
                double radius = 35 + Math.sqrt((i * .61803398875 + .19) % 1) * 210;
                double x = regions[region][0] * extent + Math.cos(angle) * radius * 1.15;
                double z = regions[region][1] * extent + Math.sin(angle) * radius * .8;
                if (Math.abs(x) > extent - 40 || Math.abs(z) > extent - 40) continue;
                if (anchors.stream().anyMatch(p -> Math.hypot(p.x() - x, p.z() - z) < 120)) continue;
                Vec3 normal = TerrainFactory.normalAt(anchors, x, z);
                boolean tall = kind == Kind.TREE || kind == Kind.DEAD_TREE;
                if (-normal.y() < (tall ? .5 : .32)) continue;
                Vec3 at = new Vec3(x, -TerrainFactory.meshHeightAt(anchors, x, z, simple), z);
                double height = tall ? 70 + (id * 17 % 65) : kind == Kind.SHRUB ? 13 + id % 12 : kind == Kind.GRASS ? 7 + id % 8 : 14 + id % 12;
                plants.add(new Plant(id, kind, at, height));
                if (kind == Kind.TREE || kind == Kind.DEAD_TREE)
                    tree(geometry, at, height, id, kind == Kind.DEAD_TREE, simple);
                else if (kind == Kind.SHRUB) {
                    patch(geometry, anchors, at, 27, id, simple);
                    for (int n = 0; n < (simple ? 1 : 3); n++)
                        geometry.organic(n == 0 ? ScenePalette.NEEDLES : ScenePalette.LEAVES,
                                at.add(new Vec3((n - 1) * 9, -height * .55, n % 2 * 7)), new Vec3(13, height * .65, 12), id + n);
                } else if (kind == Kind.GRASS) {
                    grass(geometry, anchors, at, height, id, simple);
                } else
                    geometry.organic(ScenePalette.ROCK_MOSS, at.add(new Vec3(0, -height * .25, 0)), new Vec3(height, height * .7, height * .8), id);
            }
        return new Vegetation(plants, geometry.build());
    }

    private static void tree(GeometryBuilder b, Vec3 at, double height, int id, boolean dead, boolean simple) {
        Vec3 top = at.add(new Vec3(height * .12, -height * (dead ? 1 : .91), 2));
        b.beam(ScenePalette.BARK, at.add(new Vec3(0, 3, 0)), top, dead ? 1.8 : 2.2);
        for (int n = 0; n < (simple ? 1 : 3); n++) {
            double angle = n * 2.4 + id;
            Vec3 start = at.add(new Vec3(height * .06, -height * (.4 + n * .12), 0));
            Vec3 end = start.add(new Vec3(Math.cos(angle) * height * .27, -height * .18, Math.sin(angle) * height * .27));
            b.beam(ScenePalette.BARK, start, end, .8);
            if (dead) b.beam(ScenePalette.BARK, end, end.add(new Vec3(4, -9, 3)), .4);
        }
        if (!dead) for (int n = 0; n < (simple ? 2 : 5); n++) {
            double angle = n * 2.4 + id;
            double breadth = height * (n == 4 ? .17 : .23);
            Vec3 center = at.add(new Vec3(Math.cos(angle) * height * .13 + height * .08, -height * (.61 + n * .055), Math.sin(angle) * height * .13));
            b.organic(n % 2 == 0 ? ScenePalette.NEEDLES : ScenePalette.LEAVES, center, new Vec3(breadth, height * .21, breadth * .85), id + n);
        }
    }

    private static void grass(GeometryBuilder b, List<Vec3> anchors, Vec3 at, double height, int id, boolean simple) {
        for (int n = 0; n < (simple ? 3 : 9); n++) {
            double angle = n * 2.399963 + id;
            Vec3 base = at.add(new Vec3(Math.cos(angle) * n * .9, 0, Math.sin(angle) * n * .9));
            base = new Vec3(base.x(), -TerrainFactory.meshHeightAt(anchors, base.x(), base.z(), simple) - .3, base.z());
            Vec3 across = new Vec3(Math.cos(angle) * 1.1, 0, Math.sin(angle) * 1.1);
            Vec3 tip = base.add(new Vec3(height * .28, -height * (.65 + n * .04), -height * .15));
            b.polygon(n % 3 == 0 ? ScenePalette.LEAVES : ScenePalette.GRASS, base.subtract(across), base.add(across), tip);
        }
    }

    private static void patch(GeometryBuilder b, List<Vec3> anchors, Vec3 at, double radius, int id, boolean simple) {
        Vec3 center = at.add(new Vec3(0, -.7, 0));
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, c = (i + 1) * Math.PI / 6;
            double r = radius * (.9 + .16 * Math.sin(i * 3.31 + id)), s = radius * (.9 + .16 * Math.sin((i + 1) * 3.31 + id));
            double x = at.x() + Math.cos(a) * r, z = at.z() + Math.sin(a) * r;
            double tx = at.x() + Math.cos(c) * s, tz = at.z() + Math.sin(c) * s;
            b.polygon(ScenePalette.MOSS, center, new Vec3(x, -TerrainFactory.meshHeightAt(anchors, x, z, simple) - .7, z),
                    new Vec3(tx, -TerrainFactory.meshHeightAt(anchors, tx, tz, simple) - .7, tz));
        }
    }

}
