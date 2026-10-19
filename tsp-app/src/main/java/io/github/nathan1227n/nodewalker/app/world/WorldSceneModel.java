package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.List;

/** Geometry-only renderer boundary. It carries no common mathematical certification. */
public interface WorldSceneModel {
    interface Edge {
        int from();
        int to();
        long matrixCost();
        SegmentGeometry geometry();
    }
    PreparedWorld preparedWorld();
    default TspProblem problem() { return preparedWorld().problem(); }
    default List<PreparedWorld.Refuge> refuges() { return preparedWorld().refuges(); }
    default List<Vec3> positions() { return preparedWorld().positions(); }
    default TerrainData terrain(boolean simple) { return geometry(simple).terrain(); }
    default WorldGeometry geometry(boolean simple) { return preparedWorld().geometry(simple); }
    default MapProjection projection() { return preparedWorld().projection(); }
    List<? extends Edge> segments();
    List<? extends Edge> secondaryConnections(int selected);
    boolean solidPath();
    default java.util.Set<Integer> labelPriorities() { return java.util.Set.of(0); }
    default String vertexTag(int index) { return index == 0 ? "BASE 0" : ""; }
    default boolean priorityVertex(int index) { return index == 0; }
}
