import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopology;
import io.github.nathan1227n.nodewalker.core.graph.RefugeTopologyGenerator;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import java.util.Arrays;

/** Opt-in headless experiment. No TSP solver is executed. See docs/CAMINHOS_MINIMOS.md. */
public class ShortestPathBenchmark {
    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("No arguments expected.");
        var options = ShortestPathExecutionOptions.demonstration();
        System.out.println("Java=" + System.getProperty("java.version") + ", OS=" + System.getProperty("os.name")
                + ", arch=" + System.getProperty("os.arch") + ", warmup=2, runs=5, options=" + options);
        System.out.println("N,seed,topology,edges,status,vertices,examined,attempts,improved,peakQueue,medianInternalNanos,destination,distance,path");
        for (int n : new int[] {8, 32, 128}) {
            for (long seed : new long[] {0, 657}) {
                var problem = TspGenerator.generate(n, seed, new GenerationOptions(100_000, 64L * 1024 * 1024));
                for (var topology : new RefugeTopology[] {RefugeTopology.complete(n),
                        RefugeTopologyGenerator.connectedSparse(n, seed, n / 2)}) {
                    var graph = new RefugeGraph(problem, topology);
                    var solver = new DijkstraShortestPathSolver();
                    ShortestPathResult reference = null;
                    long[] times = new long[5];
                    boolean completed = true;
                    for (int run = -2; run < 5; run++) {
                        var result = solver.solve(graph, options, new ShortestPathCancellationToken());
                        completed &= result.status() == ShortestPathStatus.CONCLUIDO;
                        if (reference == null) reference = result;
                        if (result.status() == ShortestPathStatus.CONCLUIDO
                                && reference.status() == ShortestPathStatus.CONCLUIDO) {
                            for (int v = 0; v < n; v++) {
                                if (!reference.distanceTo(v).equals(result.distanceTo(v))
                                        || !reference.pathTo(v).equals(result.pathTo(v))) {
                                    throw new IllegalStateException("Nondeterministic result");
                                }
                            }
                        }
                        if (run >= 0) times[run] = result.metrics().elapsedNanos().orElseThrow();
                    }
                    Arrays.sort(times);
                    var m = reference.metrics();
                    System.out.println(n + "," + seed + "," + topology.kind() + "," + topology.edgeCount()
                            + "," + reference.status() + "," + m.processedVertices() + "," + m.examinedEdges()
                            + "," + m.relaxationAttempts() + "," + m.successfulRelaxations() + "," + m.peakQueueSize()
                            + "," + (completed ? times[2] : "NA") + "," + (n - 1) + ","
                            + reference.distanceTo(n - 1).map(Object::toString).orElse("NA") + ",\""
                            + reference.pathTo(n - 1).map(Object::toString).orElse("NA") + "\"");
                }
            }
        }
    }
}
