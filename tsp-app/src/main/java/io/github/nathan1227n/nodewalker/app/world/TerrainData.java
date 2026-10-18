package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/** Prepared off the FX thread. All collections and elements are immutable. */
public record TerrainData(List<Vec3> vertices, List<Face> faces, double extent) {
    public TerrainData { vertices = List.copyOf(vertices); faces = List.copyOf(faces); }
    public record Face(int a, int b, int c, int material) { }
}
