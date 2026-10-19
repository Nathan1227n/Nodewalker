package io.github.nathan1227n.nodewalker.core.shortestpath;

import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/**
 * Classical single-source Dijkstra on available refuge connections, not a TSP solver.
 * Nonnegative matrix weights, indexed heap, strict relaxations; first equal predecessor wins.
 * Heap ties use vertex indices; adjacency order is ascending. No lexicographic-path promise.
 * O((V+E) log V) arithmetic operations, O(V) workspace; BigInteger arithmetic depends on bit length.
 * All state is local to a call. JVM Errors propagate; RuntimeExceptions during execution yield ERRO.
 */
public final class DijkstraShortestPathSolver {
    enum Phase { PREPARATION, ALLOCATION, BEFORE_VERTEX, AFTER_FIXATION, BEFORE_EDGE, FINALIZATION }

    @FunctionalInterface
    interface Observer {
        void checkpoint(Phase phase, long processedVertices, long examinedEdges);
    }

    private final LongSupplier clock;
    private final Observer observer;

    public DijkstraShortestPathSolver() { this(System::nanoTime, null); }

    DijkstraShortestPathSolver(LongSupplier clock, Observer observer) {
        this.clock = Objects.requireNonNull(clock);
        this.observer = observer;
    }

    /** Default origin 0, explicit demonstration budgets (not an unlimited legacy entry point). */
    public ShortestPathResult solve(RefugeGraph graph) {
        return solve(graph, 0, ShortestPathExecutionOptions.demonstration(), new ShortestPathCancellationToken());
    }

    public ShortestPathResult solve(RefugeGraph graph, ShortestPathExecutionOptions options,
                                    ShortestPathCancellationToken token) {
        return solve(graph, 0, options, token);
    }

    /** Invalid arguments/resource refusal throw before execution. Only fixed labels survive interruption. */
    public ShortestPathResult solve(RefugeGraph graph, int origin, ShortestPathExecutionOptions options,
                                    ShortestPathCancellationToken token) {
        Objects.requireNonNull(graph);
        Objects.checkIndex(origin, graph.size());
        Objects.requireNonNull(options);
        Objects.requireNonNull(token);
        ShortestPathPreflight.analyze(graph, options).requireAllowed();
        Run run = new Run(graph, origin, options, token);
        try {
            run.start = clock.getAsLong();
            run.started = true;
            run.check(Phase.PREPARATION);
            run.check(Phase.ALLOCATION); // vertex=0 budget blocks workspace allocation
            run.labels = new BigInteger[graph.size()];
            run.parents = new int[graph.size()];
            run.rank = new int[graph.size()];
            Arrays.fill(run.parents, -1);
            var heap = new IndexedVertexHeap(run.labels);
            run.labels[origin] = BigInteger.ZERO;
            heap.insertOrDecrease(origin);
            run.peak = 1;
            while (heap.size() != 0) {
                run.check(Phase.BEFORE_VERTEX);
                int current = heap.removeMin();
                run.processed = Math.incrementExact(run.processed);
                run.rank[current] = Math.toIntExact(run.processed);
                run.check(Phase.AFTER_FIXATION);
                for (int position = 0; position < graph.topology().degree(current); position++) {
                    run.check(Phase.BEFORE_EDGE);
                    run.examined = Math.incrementExact(run.examined);
                    int next = graph.topology().neighborAt(current, position);
                    if (run.rank[next] != 0) continue;
                    run.attempts = Math.incrementExact(run.attempts);
                    BigInteger candidate = run.labels[current].add(BigInteger.valueOf(graph.cost(current, next)));
                    if (run.labels[next] == null || candidate.compareTo(run.labels[next]) < 0) {
                        run.labels[next] = candidate;
                        run.parents[next] = current;
                        run.improvements = Math.incrementExact(run.improvements);
                        heap.insertOrDecrease(next);
                        run.peak = Math.max(run.peak, heap.size());
                    }
                }
            }
            run.check(Phase.FINALIZATION);
            return run.finish(ShortestPathStatus.CONCLUIDO, null);
        } catch (Stopped stopped) {
            return run.finish(stopped.status, stopped.getMessage());
        } catch (RuntimeException failure) {
            return run.finish(ShortestPathStatus.ERRO, diagnostic(failure));
        }
    }

    private final class Run {
        private final RefugeGraph graph;
        private final int origin;
        private final ShortestPathExecutionOptions options;
        private final ShortestPathCancellationToken token;
        private BigInteger[] labels;
        private int[] parents, rank;
        private long start, processed, examined, attempts, improvements, peak;
        private boolean started, stopped;
        private OptionalLong elapsed = OptionalLong.empty();

        Run(RefugeGraph graph, int origin, ShortestPathExecutionOptions options, ShortestPathCancellationToken token) {
            this.graph = graph;
            this.origin = origin;
            this.options = options;
            this.token = token;
        }

        long duration() {
            long value = clock.getAsLong() - start; // deliberate modular subtraction for nanoTime wrap
            if (value < 0) throw new IllegalStateException("Unrepresentable or negative elapsed time.");
            return value;
        }

        void check(Phase phase) {
            if (observer != null) observer.checkpoint(phase, processed, examined);
            boolean cancelled = token.isCancellationRequested();
            boolean interrupted = Thread.currentThread().isInterrupted();
            boolean timedOut = duration() >= options.maxElapsedMillis() * 1_000_000;
            boolean vertices = (phase == Phase.BEFORE_VERTEX || phase == Phase.ALLOCATION)
                    && processed >= options.maxProcessedVertices();
            boolean edges = phase == Phase.BEFORE_EDGE && examined >= options.maxExaminedEdges();
            if (cancelled || interrupted || timedOut || vertices || edges) {
                String reason = "Interruption: " + (cancelled || interrupted ? "cancellation" : timedOut ? "time"
                        : vertices ? "vertices" : "edges") + "; phase=" + phase
                        + "; token=" + cancelled + "; interrupted=" + interrupted + "; time=" + timedOut
                        + "; vertices=" + vertices + "; edges=" + edges
                        + "; processed=" + processed + "; examined=" + examined;
                throw new Stopped(cancelled || interrupted ? ShortestPathStatus.CANCELADO
                        : ShortestPathStatus.LIMITE_ATINGIDO, reason);
            }
        }

        ShortestPathResult finish(ShortestPathStatus status, String reason) {
            if (started && !stopped) {
                stopped = true;
                try {
                    elapsed = OptionalLong.of(duration());
                } catch (RuntimeException failure) {
                    if (status == ShortestPathStatus.CONCLUIDO) status = ShortestPathStatus.ERRO;
                    reason = append(reason, "Timing failure: " + diagnostic(failure));
                }
            }
            var metrics = new ShortestPathMetrics(elapsed, processed, examined, attempts, improvements, peak);
            try {
                return ShortestPathResult.create(graph, origin, status, labels, parents, rank, metrics, reason);
            } catch (RuntimeException failure) {
                return ShortestPathResult.create(graph, origin,
                        status == ShortestPathStatus.CONCLUIDO ? ShortestPathStatus.ERRO : status,
                        null, null, null, metrics,
                        append(reason, "Result validation failure: " + diagnostic(failure)));
            }
        }
    }

    private static String diagnostic(RuntimeException failure) {
        return failure.getClass().getName() + ": " + failure.getMessage();
    }

    private static String append(String reason, String extra) { return reason == null ? extra : reason + "; " + extra; }

    private static final class Stopped extends RuntimeException {
        private final ShortestPathStatus status;
        Stopped(ShortestPathStatus status, String reason) {
            super(reason, null, false, false);
            this.status = status;
        }
    }
}
