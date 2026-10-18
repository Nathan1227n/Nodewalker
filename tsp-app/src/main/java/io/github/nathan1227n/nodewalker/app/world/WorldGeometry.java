package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Prepared once per instance/quality on the worker; result-only changes share this whole object.
 */
public record WorldGeometry(TerrainData terrain, TerrainData horizon,
                            List<RefugeGeometryFactory.RefugeGeometry> refuges,
                            PropGeometryFactory.Decoration decoration) {
    public WorldGeometry {
        refuges = List.copyOf(refuges);
    }

    public static WorldGeometry prepare(List<Vec3> anchors, boolean simple) {
        return new WorldGeometry(TerrainFactory.build(anchors, simple), TerrainFactory.horizon(anchors, simple),
                IntStream.range(0, anchors.size()).mapToObj(i -> RefugeGeometryFactory.build(i, anchors.get(i),
                        -TerrainFactory.heightAt(anchors, anchors.get(i).x(), anchors.get(i).z()), simple)).toList(),
                PropGeometryFactory.build(anchors, simple));
    }

    public long triangleCount() {
        return terrain.faces().size() + horizon.faces().size() + meshes().stream().mapToLong(m -> m.triangles().size()).sum();
    }

    public List<GeometryMesh> meshes() {
        return java.util.stream.Stream.concat(refuges.stream().flatMap(r -> r.meshes().stream()), decoration.meshes().stream()).toList();
    }

    /**
     * Numeric mesh buffers plus the 256x128 RGBA terrain ramp; excludes JVM/JavaFX/driver overhead.
     */
    public long numericBufferBytes() {
        return (terrain.vertices().size() + horizon.vertices().size()) * 32L
                + (terrain.faces().size() + horizon.faces().size()) * 36L
                + meshes().stream().mapToLong(GeometryMesh::bufferBytes).sum() + 256 * 128 * 4;
    }
}
