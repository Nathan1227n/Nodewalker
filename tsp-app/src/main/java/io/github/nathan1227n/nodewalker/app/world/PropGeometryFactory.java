package io.github.nathan1227n.nodewalker.app.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Authored deterministic sites, with a city exclusion radius and no luminous city-like markers.
 */
public final class PropGeometryFactory {
    public enum Kind {BROKEN_RELAY, INDUSTRIAL_RUIN, SUPPLY_DEPOT, ROCKS}

    public record Site(Kind kind, Vec3 position) {
    }

    public record Decoration(List<Site> sites, List<GeometryMesh> meshes) {
        public Decoration {
            sites = List.copyOf(sites);
            meshes = List.copyOf(meshes);
        }
    }

    private PropGeometryFactory() {
    }

    public static Decoration build(List<Vec3> anchors, boolean simple) {
        var sites = new ArrayList<Site>();
        var b = new GeometryBuilder(Vec3.ZERO, 1);
        double extent = TerrainFactory.extent(anchors);
        int count = simple ? 7 : 24;
        for (int i = 0; i < count; i++) {
            // Sites form peripheral regions rather than filling the city group with random objects.
            double angle = i * 2.399963229728653;
            double radius = extent * (.62 + (i % 4) * .1);
            double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
            if (anchors.stream().anyMatch(p -> Math.hypot(p.x() - x, p.z() - z) < 120)) continue;
            Vec3 at = new Vec3(x, -TerrainFactory.meshHeightAt(anchors, x, z, simple), z);
            Kind kind = i < 3 ? Kind.values()[i] : Kind.ROCKS;
            sites.add(new Site(kind, at));
            if (kind == Kind.BROKEN_RELAY) {
                RefugeGeometryFactory.tower(b, at, 110, simple);
                b.beam(ScenePalette.RUST, at.add(new Vec3(0, -110, 0)), at.add(new Vec3(26, -132, 9)), 1.5);
                b.box(ScenePalette.STEEL, at.add(new Vec3(0, -13, 0)), 34, 26, 19, 0, 0);
            } else if (kind == Kind.INDUSTRIAL_RUIN) {
                b.cabin(ScenePalette.CONCRETE, at.add(new Vec3(-18, -17, 0)), 31, 34, 11);
                b.box(ScenePalette.CONCRETE, at.add(new Vec3(22, -24, 10)), 8, 48, 41, 0, 0);
                b.beam(ScenePalette.RUST, at.add(new Vec3(-27, -37, 0)), at.add(new Vec3(18, -17, 12)), 1.8);
                b.beam(ScenePalette.STEEL, at.add(new Vec3(5, -8, -10)), at.add(new Vec3(34, -53, 10)), 1);
            } else if (kind == Kind.SUPPLY_DEPOT) {
                b.box(ScenePalette.RUST, at.add(new Vec3(0, -10, 0)), 40, 20, 18, 17, 0);
                b.box(ScenePalette.STEEL, at.add(new Vec3(24, -5, 7)), 17, 10, 13, -9, 0);
                RefugeGeometryFactory.generator(b, at.add(new Vec3(-23, -5, -11)));
                b.beam(ScenePalette.METAL, at.add(new Vec3(-31, 0, 10)), at.add(new Vec3(-31, -36, 10)), .7);
            } else {
                for (int j = 0; j < (simple ? 1 : 3); j++) {
                    Vec3 p = at.add(new Vec3(j * 15, -3 - j * 2, j * 9));
                    b.organic(j % 2 == 0 ? ScenePalette.ROCK_STONE : ScenePalette.ROCK_SHALE, p,
                            new Vec3(14 + i % 4 * 5, 10 + i % 3 * 5, 15 + j * 4), i * 3 + j);
                }
            }
        }
        return new Decoration(sites, b.build());
    }
}
