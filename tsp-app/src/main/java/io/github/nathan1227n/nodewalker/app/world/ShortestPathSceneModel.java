package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathResult;
import java.util.*;

/** A shortest path presentation, never a TspRoute/TspResult or a closed tour. */
public final class ShortestPathSceneModel implements WorldSceneModel {
    public static final int MAX_SECONDARY_EDGES = 256;
    public record PathSegment(int from, int to, long matrixCost, SegmentGeometry geometry) implements Edge { }
    private final PreparedWorld world;
    private final CostBreakdown costBreakdown;
    private final RefugeGraph graph;
    private final int origin, destination;
    private final Optional<ShortestPathResult> result;
    private final List<PathSegment> segments;
    private final List<PathSegment> connections;

    public ShortestPathSceneModel(PreparedWorld world, RefugeGraph graph, int origin, int destination,
                                  Optional<ShortestPathResult> result) {
        this.world = Objects.requireNonNull(world);
        this.graph = Objects.requireNonNull(graph);
        if (world.problem() != graph.problem()) throw new IllegalArgumentException("Different instance");
        Objects.checkIndex(origin, graph.size()); Objects.checkIndex(destination, graph.size());
        this.origin = origin; this.destination = destination; this.result = Objects.requireNonNull(result);
        result.ifPresent(r -> {
            if (r.graph() != graph || r.origin() != origin) throw new IllegalArgumentException("Different graph/origin");
        });
        this.costBreakdown = CostBreakdown.shortestPath(graph,origin,destination,result);
        var path = result.flatMap(r -> r.pathTo(destination)).orElse(List.of());
        var edges = new ArrayList<PathSegment>();
        for (int i = 1; i < path.size(); i++) edges.add(edge(path.get(i - 1), path.get(i)));
        segments = List.copyOf(edges);
        // The visual subset is independent of the logical graph and never filters the highlighted path.
        var secondary = new LinkedHashMap<Long, PathSegment>();
        if (graph.topology().kind() == io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Kind.COMPLETE) {
            for (int from : new int[]{origin, destination}) addIncident(secondary, from);
        } else {
            outer: for (int from = 0; from < graph.size(); from++)
                for (int j = 0; j < graph.topology().degree(from); j++) {
                    int to = graph.topology().neighborAt(from, j);
                    if (from < to) put(secondary, from, to);
                    if (secondary.size() >= MAX_SECONDARY_EDGES) break outer;
                }
        }
        connections = List.copyOf(secondary.values());
    }
    private PathSegment edge(int from, int to) {
        return new PathSegment(from, to, graph.cost(from, to), new SegmentGeometry(world.positions().get(from), world.positions().get(to)));
    }
    private void put(Map<Long, PathSegment> edges, int from, int to) {
        int a = Math.min(from, to), b = Math.max(from, to);
        if (segments.stream().anyMatch(e -> Math.min(e.from(), e.to()) == a && Math.max(e.from(), e.to()) == b)) return;
        edges.putIfAbsent(((long) a << 32) | b, edge(a, b));
    }
    private void addIncident(Map<Long, PathSegment> edges, int from) {
        for (int j = 0; j < graph.topology().degree(from) && edges.size() < MAX_SECONDARY_EDGES; j++)
            put(edges, from, graph.topology().neighborAt(from, j));
    }
    public CostBreakdown costBreakdown() { return costBreakdown; }
    @Override public PreparedWorld preparedWorld() { return world; }
    public RefugeGraph graph() { return graph; }
    public int origin() { return origin; }
    public int destination() { return destination; }
    public Optional<ShortestPathResult> result() { return result; }
    @Override public List<PathSegment> segments() { return segments; }
    @Override public List<PathSegment> secondaryConnections(int selected) { return connections; }
    @Override public boolean solidPath() { return result.map(r -> r.distanceTo(destination).isPresent()).orElse(false); }
    @Override public boolean priorityVertex(int index) { return index == 0 || index == origin || index == destination; }
    @Override public Set<Integer> labelPriorities() { return Set.copyOf(List.of(0, origin, destination)); }
    @Override public String vertexTag(int index) {
        return (index == 0 ? "BASE 0 " : "") + (index == origin ? "[O] " : "") + (index == destination ? "[D] " : "");
    }
    public static String topologyLabel(io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Kind kind) {
        return switch (kind) { case COMPLETE -> "Completa (implícita)"; case SPARSE_CONNECTED -> "Esparsa conectada"; case MANUAL -> "Manual"; };
    }
    public String connectionLabel() {
        long visible = connections.size() + segments.size();
        return topologyLabel(graph.topology().kind()) + " · " + graph.topology().edgeCount() + " conexões"
                + " · " + visible + " na camada visual" + (visible < graph.topology().edgeCount() ? " (filtro visual)" : "");
    }
}
