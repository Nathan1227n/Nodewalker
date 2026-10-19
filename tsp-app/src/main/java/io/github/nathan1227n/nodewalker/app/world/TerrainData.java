package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;

/**
 * Prepared off the FX thread. All collections and elements are immutable.
 */
public record TerrainData(List<Vec3> vertices, List<Face> faces, double extent, List<Vec3> normals) {
    public TerrainData {
        vertices = List.copyOf(vertices);
        faces = List.copyOf(faces);
        normals = List.copyOf(normals);
    }

    public TerrainData(List<Vec3> vertices, List<Face> faces, double extent) {
        this(vertices, faces, extent, normals(vertices, faces));
    }

    private static List<Vec3> normals(List<Vec3> vertices, List<Face> faces) {
        double[][] sums = new double[vertices.size()][3];
        for (Face f : faces) {
            Vec3 normal = vertices.get(f.b).subtract(vertices.get(f.a)).cross(vertices.get(f.c).subtract(vertices.get(f.a)));
            for (int at : new int[]{f.a, f.b, f.c}) {
                sums[at][0] += normal.x();
                sums[at][1] += normal.y();
                sums[at][2] += normal.z();
            }
        }
        var result = new java.util.ArrayList<Vec3>(vertices.size());
        for (double[] n : sums) result.add(new Vec3(n[0], n[1], n[2]).unit());
        return List.copyOf(result);
    }

    public record Face(int a, int b, int c, int material) {
    }
}
