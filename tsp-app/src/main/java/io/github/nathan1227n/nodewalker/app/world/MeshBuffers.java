package io.github.nathan1227n.nodewalker.app.world;

/** Numeric/UV preparation uses no JavaFX. Accessors return copies for exclusive renderer ownership. */
public final class MeshBuffers {
    private final float[] points, normals, uv;
    private final int[] faces;
    private MeshBuffers(float[] points, float[] normals, float[] uv, int[] faces) {
        this.points = points; this.normals = normals; this.uv = uv; this.faces = faces;
    }
    public float[] points() { return points.clone(); }
    public float[] normals() { return normals.clone(); }
    public float[] uv() { return uv.clone(); }
    public int[] faces() { return faces.clone(); }
    public static MeshBuffers of(TerrainData terrain, Runnable checkpoint) {
        float[] points = coordinates(terrain.vertices()), normals = coordinates(terrain.normals());
        int[] faces = new int[terrain.faces().size() * 9];
        float[] uv = new float[terrain.faces().size() * 6];
        for (int i = 0; i < terrain.faces().size(); i++) {
            checkpoint.run();
            var f = terrain.faces().get(i);
            map(terrain.vertices(), new int[]{f.a(), f.b(), f.c()}, i, 240, faces, uv, true);
        }
        return new MeshBuffers(points, normals, uv, faces);
    }
    public static MeshBuffers of(GeometryMesh mesh, Runnable checkpoint) {
        int[] faces = new int[mesh.triangles().size() * 6];
        float[] uv = new float[mesh.triangles().size() * 6];
        for (int i = 0; i < mesh.triangles().size(); i++) {
            checkpoint.run();
            var f = mesh.triangles().get(i);
            map(mesh.vertices(), new int[]{f.a(), f.b(), f.c()}, i, 36, faces, uv, false);
        }
        return new MeshBuffers(coordinates(mesh.vertices()), new float[0], uv, faces);
    }
    private static float[] coordinates(java.util.List<Vec3> vertices) {
        var values = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            var p = vertices.get(i);
            values[i * 3] = (float) p.x(); values[i * 3 + 1] = (float) p.y(); values[i * 3 + 2] = (float) p.z();
        }
        return values;
    }
    private static void map(java.util.List<Vec3> points, int[] at, int i, int scale, int[] faces, float[] uv, boolean normals) {
        var mapping = SurfaceMapping.triangle(points.get(at[0]), points.get(at[1]), points.get(at[2]), scale);
        int stride = normals ? 3 : 2;
        for (int j = 0; j < 3; j++) {
            int offset = (i * 3 + j) * stride;
            faces[offset] = at[j];
            if (normals) faces[offset + 1] = at[j];
            faces[offset + stride - 1] = i * 3 + j;
            uv[i * 6 + j * 2] = (float) mapping.get(j).u(); uv[i * 6 + j * 2 + 1] = (float) mapping.get(j).v();
        }
    }
}
