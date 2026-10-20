package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.City;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.*;

/** Immutable, shared by both problems. Prepare outside JavaFX, once per instance. */
public final class PreparedWorld {
    public record Refuge(City logical, Vec3 position) { public int index() { return logical.index(); } }
    private final TspProblem problem;
    private final WorldTransform transform;
    private final List<Refuge> refuges;
    private final List<Vec3> positions;
    private final WorldGeometry detailed, simple;
    private final MapProjection projection;
    private final Map<Object, MeshBuffers> buffers;

    private PreparedWorld(TspProblem problem, Runnable checkpoint) {
        this.problem = Objects.requireNonNull(problem);
        VisualBudget.requireAllowed(problem.size());
        checkpoint.run();
        transform = WorldTransform.fit(problem.cities());
        refuges = problem.cities().stream().map(c -> new Refuge(c, transform.toVisual(c))).toList();
        positions = refuges.stream().map(Refuge::position).toList();
        detailed = WorldGeometry.prepare(positions, false, checkpoint);
        simple = WorldGeometry.prepare(positions, true, checkpoint);
        projection = MapProjection.fit(positions);
        var prepared = new IdentityHashMap<Object, MeshBuffers>();
        for (var geometry : List.of(detailed, simple)) {
            checkpoint.run();
            prepared.put(geometry.terrain(), MeshBuffers.of(geometry.terrain(), checkpoint));
            prepared.put(geometry.horizon(), MeshBuffers.of(geometry.horizon(), checkpoint));
            for (var mesh : geometry.meshes()) {
                checkpoint.run();
                prepared.put(mesh, MeshBuffers.of(mesh, checkpoint));
            }
        }
        buffers = Collections.unmodifiableMap(prepared);
    }
    public static PreparedWorld prepare(TspProblem problem, Runnable checkpoint) { return new PreparedWorld(problem, checkpoint); }
    public static PreparedWorld prepare(TspProblem problem) { return prepare(problem, () -> { }); }
    public TspProblem problem() { return problem; }
    public WorldTransform transform() { return transform; }
    public List<Refuge> refuges() { return refuges; }
    public List<Vec3> positions() { return positions; }
    public WorldGeometry geometry(boolean simplified) { return simplified ? simple : detailed; }
    public MapProjection projection() { return projection; }
    public MeshBuffers buffers(Object geometry) { return Objects.requireNonNull(buffers.get(geometry), "Unprepared geometry"); }
}
