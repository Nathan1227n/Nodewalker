package io.github.nathan1227n.nodewalker.app.world;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Prepared once per instance/quality on the worker; result-only changes share this whole object.
 */
public record WorldGeometry(TerrainData terrain, TerrainData horizon,
                            List<RefugeGeometryFactory.RefugeGeometry> refuges,
                            PropGeometryFactory.Decoration decoration, VegetationFactory.Vegetation vegetation) {
    public WorldGeometry {
        refuges = List.copyOf(refuges);
    }

    public static WorldGeometry prepare(List<Vec3> anchors, boolean simple) {
        return new WorldGeometry(TerrainFactory.build(anchors, simple), TerrainFactory.horizon(anchors, simple),
                IntStream.range(0, anchors.size()).mapToObj(i -> RefugeGeometryFactory.build(i, anchors.get(i),
                        -TerrainFactory.heightAt(anchors, anchors.get(i).x(), anchors.get(i).z()), simple)).toList(),
                PropGeometryFactory.build(anchors, simple), VegetationFactory.prepare(anchors, simple));
    }

    public long triangleCount() {
        return terrain.faces().size() + horizon.faces().size() + meshes().stream().mapToLong(m -> m.triangles().size()).sum();
    }

    public List<GeometryMesh> meshes() {
        return java.util.stream.Stream.of(refuges.stream().flatMap(r -> r.meshes().stream()), decoration.meshes().stream(), vegetation.meshes().stream())
                .flatMap(s -> s).toList();
    }

    /**
     * Numeric buffers plus shared RGBA rock (1024²) and foliage (512²); excludes JVM/JavaFX/driver overhead.
     */
    public long numericBufferBytes() {
        return (terrain.vertices().size() + horizon.vertices().size()) * 24L
                + (terrain.faces().size() + horizon.faces().size()) * 60L
                + meshes().stream().mapToLong(m -> m.bufferBytes() + m.triangles().size() * 24L).sum() + (1024 * 1024 + 512 * 512) * 4;
    }
}
