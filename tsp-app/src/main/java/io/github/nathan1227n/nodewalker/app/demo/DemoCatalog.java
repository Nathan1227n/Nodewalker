package io.github.nathan1227n.nodewalker.app.demo;

import io.github.nathan1227n.nodewalker.core.generator.GenerationCancellationToken;
import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.solver.BacktrackingSolver;
import io.github.nathan1227n.nodewalker.core.solver.SolverAlgorithm;
import io.github.nathan1227n.nodewalker.core.solver.SolverCancellationToken;
import io.github.nathan1227n.nodewalker.core.solver.SolverExecutionOptions;
import io.github.nathan1227n.nodewalker.core.solver.SolverPreflight;
import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import java.util.Arrays;
import java.util.Optional;

/** Fixed reproducible examples, not the operational configuration UI planned for #12. */
public enum DemoCatalog {
    TRIANGLE(3, 23, 10_000),
    OUTPOSTS(5, 23, 50_000),
    VALLEY(8, 657, 100_000),
    FRONTIER(12, 23, 50_000);

    public static final long GENERATION_CANDIDATES = 100_000;
    public static final long GENERATION_BYTES = 16 * 1024 * 1024;
    public static final long SOLVER_MILLIS = 2_000;
    private final int n;
    private final long seed;
    private final long nodes;

    DemoCatalog(int n, long seed, long nodes) { this.n = n; this.seed = seed; this.nodes = nodes; }
    public int cityCount() { return n; }
    public long seed() { return seed; }
    public long nodeBudget() { return nodes; }
    public SolverExecutionOptions options() { return SolverExecutionOptions.limits(nodes, SOLVER_MILLIS); }
    public SolverPreflight preflight() { return SolverPreflight.analyze(SolverAlgorithm.BACKTRACKING, n, options()); }

    public TspSceneModel prepare(GenerationCancellationToken generationToken, SolverCancellationToken solverToken) {
        preflight().requireAllowed();
        var problem = TspGenerator.generate(n, seed,
                new GenerationOptions(GENERATION_CANDIDATES, GENERATION_BYTES, generationToken));
        var result = new BacktrackingSolver().solve(problem, options(), solverToken);
        return TspSceneModel.of(problem, Optional.of(result));
    }

    public static DemoCatalog forSize(int n) {
        return Arrays.stream(values()).filter(d -> d.n == n).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Demo sizes: 3, 5, 8, 12"));
    }
    @Override public String toString() { return "N=" + n + "  /  seed " + seed; }
}
