package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/**
 * Static authored mesh, grouped by shared material; immutable and independent of JavaFX.
 */
public record GeometryMesh(ScenePalette material, List<Vec3> vertices, List<Triangle> triangles) {
    public GeometryMesh {
        vertices = List.copyOf(vertices);
        triangles = List.copyOf(triangles);
    }

    public record Triangle(int a, int b, int c) {
    }

    public long bufferBytes() {
        return vertices.size() * 12L + triangles.size() * 24L;
    }
}
