package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.solver.SolverMetrics;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable presentation input. Construct in background: validation and terrain preparation use no FX objects.
 * The original immutable problem/result remain authoritative. This class does not prove optimality.
 */
public final class TspSceneModel {
    private final TspProblem problem;
    private final Optional<TspResult> result;
    private final WorldTransform transform;
    private final List<Refuge> refuges;
    private final List<Vec3> positions;
    private final List<RouteSegment> segments;
    private final TerrainData detailedTerrain;
    private final TerrainData simpleTerrain;
    private final MapProjection projection;
    private final WorldGeometry detailedGeometry, simpleGeometry;

    private TspSceneModel(TspProblem problem, Optional<TspResult> result, WorldTransform transform,
                          List<Refuge> refuges, WorldGeometry detailedGeometry, WorldGeometry simpleGeometry) {
        this.problem = Objects.requireNonNull(problem);
        this.result = Objects.requireNonNull(result);
        this.transform = transform;
        this.refuges = List.copyOf(refuges);
        this.positions = this.refuges.stream().map(Refuge::position).toList();
        this.detailedGeometry = detailedGeometry;
        this.simpleGeometry = simpleGeometry;
        this.detailedTerrain = detailedGeometry.terrain();
        this.simpleTerrain = simpleGeometry.terrain();
        this.projection = MapProjection.fit(positions());
        var edges = new ArrayList<RouteSegment>();
        result.flatMap(TspResult::route).ifPresent(route -> {
            long actual = RouteValidator.validateAndCost(problem, route);
            if (actual != result.orElseThrow().cost().orElseThrow()) {
                throw new IllegalArgumentException("Result cost does not match the supplied problem matrix");
            }
            List<Integer> indices = route.indices();
            for (int i = 0; i + 1 < indices.size(); i++) {
                int from = indices.get(i), to = indices.get(i + 1);
                edges.add(new RouteSegment(i, from, to, problem.cost(from, to),
                        new SegmentGeometry(refuges.get(from).position(), refuges.get(to).position())));
            }
        });
        this.segments = List.copyOf(edges);
    }

    public static TspSceneModel of(TspProblem problem, Optional<TspResult> result) {
        Objects.requireNonNull(problem);
        WorldTransform transform = WorldTransform.fit(problem.cities());
        List<Refuge> refuges = problem.cities().stream().map(city -> new Refuge(city, transform.toVisual(city))).toList();
        List<Vec3> positions = refuges.stream().map(Refuge::position).toList();
        return new TspSceneModel(problem, result, transform, refuges,
                WorldGeometry.prepare(positions, false), WorldGeometry.prepare(positions, true));
    }

    /**
     * Shares all prepared geometry for a new result of the same immutable problem.
     */
    public TspSceneModel withResult(Optional<TspResult> result) {
        return new TspSceneModel(problem, result, transform, refuges, detailedGeometry, simpleGeometry);
    }

    public TspProblem problem() {
        return problem;
    }

    public Optional<TspResult> result() {
        return result;
    }

    public WorldTransform transform() {
        return transform;
    }

    public List<Refuge> refuges() {
        return refuges;
    }

    public List<Vec3> positions() {
        return positions;
    }

    public List<RouteSegment> segments() {
        return segments;
    }

    public List<Integer> routeIndices() {
        return result.flatMap(TspResult::route).map(r -> r.indices()).orElse(List.of());
    }

    public TerrainData terrain(boolean simplified) {
        return simplified ? simpleTerrain : detailedTerrain;
    }

    public WorldGeometry geometry(boolean simplified) {
        return simplified ? simpleGeometry : detailedGeometry;
    }

    public MapProjection projection() {
        return projection;
    }

    public ResultPresentation presentation() {
        return ResultPresentation.of(result);
    }

    public boolean certified() {
        return presentation().certified();
    }

    public OptionalLong cost() {
        return result.map(TspResult::cost).orElse(OptionalLong.empty());
    }

    public SolverMetrics metrics() {
        return result.map(TspResult::metrics).orElseGet(SolverMetrics::empty);
    }

    public String routeLabel() {
        return presentation().routeLabel();
    }

    public String statusLabel() {
        return result.map(r -> r.status().name()).orElse("SEM RESULTADO");
    }

    /**
     * At most 32 incident non-route edges; never construct the complete O(N²) graph.
     */
    public List<RouteSegment> secondaryConnections(int selected) {
        if (selected < 0 || selected >= refuges.size()) throw new IllegalArgumentException("Invalid selection");
        var edges = new ArrayList<RouteSegment>();
        for (int to = 0; to < refuges.size() && edges.size() < 32; to++) {
            final int target = to;
            if (to == selected || segments.stream().anyMatch(s ->
                    (s.from() == selected && s.to() == target) || (s.to() == selected && s.from() == target))) continue;
            edges.add(new RouteSegment(edges.size(), selected, to, problem.cost(selected, to),
                    new SegmentGeometry(refuges.get(selected).position(), refuges.get(to).position())));
        }
        return List.copyOf(edges);
    }

    public record Refuge(City logical, Vec3 position) {
        public int index() {
            return logical.index();
        }
    }

    public record RouteSegment(int order, int from, int to, long matrixCost, SegmentGeometry geometry) {
    }
}
