import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.IntStream;

/** Reproducible actual solver output for the academic script; independent tests remain the oracles. */
public class AcademicReference {
    static List<City> cities(int n){return IntStream.range(0,n).mapToObj(i->new City(i,i*20,0,0)).toList();}
    public static void main(String[] args) {
        var professor=new TspProblem(cities(4),new long[][]{{0,1,4,6},{1,0,2,3},{4,2,0,3},{6,3,3,0}});
        for(TspSolver solver:List.of(new BruteForceSolver(),new BacktrackingSolver())) {
            var r=solver.solve(professor,SolverExecutionOptions.limits(1_000_000,2000),new SolverCancellationToken());
            System.out.println("Professor TSP | "+solver.getClass().getSimpleName()+" | "+r.status()+" | route="+r.route().orElseThrow().indices()+" | cost="+r.cost().orElseThrow()+" | evaluated="+r.metrics().completeRoutesEvaluated().orElseThrow());
            showSum(professor,r.route().orElseThrow().indices());
        }
        var p=TspGenerator.generate(5,23,new GenerationOptions(100_000,16L*1024*1024));
        for(TspSolver solver:List.of(new BruteForceSolver(),new BacktrackingSolver())) {
            var r=solver.solve(p,SolverExecutionOptions.limits(1_000_000,2000),new SolverCancellationToken());
            System.out.println("N5 seed23 | "+solver.getClass().getSimpleName()+" | "+r.status()+" | route="+r.route().orElseThrow().indices()+" | cost="+r.cost().orElseThrow());showSum(p,r.route().orElseThrow().indices());
        }
        var pathProblem=TspGenerator.generate(8,657,new GenerationOptions(100_000,16L*1024*1024));
        var graph=new RefugeGraph(pathProblem,RefugeTopologyGenerator.connectedSparse(8,23,0));
        var paths=new DijkstraShortestPathSolver().solve(graph);
        for(int destination:new int[]{5,2}) {
            System.out.println("Dijkstra N8 seed657 topologySeed23 extras0 | origin=0 | destination="+destination+" | "+paths.status()+" | distance="+paths.distanceTo(destination).orElseThrow()+" | path="+paths.pathTo(destination).orElseThrow());
            showSum(pathProblem,paths.pathTo(destination).orElseThrow());
        }
        var extreme=new TspProblem(cities(4),new long[][]{{0,Long.MAX_VALUE,0,0},{Long.MAX_VALUE,0,Long.MAX_VALUE,0},{0,Long.MAX_VALUE,0,0},{0,0,0,0}});
        var chain=new RefugeGraph(extreme,RefugeTopology.manual(4,List.of(new RefugeTopology.Edge(0,1),new RefugeTopology.Edge(1,2))));
        var exact=new DijkstraShortestPathSolver().solve(chain);
        System.out.println("BigInteger manual undirected chain | distanceTo2="+exact.distanceTo(2).orElseThrow()+" | path="+exact.pathTo(2).orElseThrow()+" | vertex3="+exact.vertexState(3));
        var limited=new DijkstraShortestPathSolver().solve(chain,ShortestPathExecutionOptions.limits(2,100,2000),new ShortestPathCancellationToken());
        System.out.println("Dijkstra K=2 | "+limited.status()+" | vertex1="+limited.vertexState(1)+" | distance1="+limited.distanceTo(1)+" | vertex2="+limited.vertexState(2)+" | distance2="+limited.distanceTo(2));
        var interrupted=new BruteForceSolver().solve(p,SolverExecutionOptions.limits(1,2000),new SolverCancellationToken());
        System.out.println("TSP K=1 | "+interrupted.status()+" | optimalityGuaranteed="+interrupted.optimalityGuaranteed()+" | cost="+interrupted.cost()+" | route="+interrupted.route().orElseThrow().indices());
        System.out.println("Dijkstra directed slide 0-2-3 cost9: OUT OF SCOPE; current topology is undirected. No directed example was executed.");
    }
    static void showSum(TspProblem p,List<Integer> route) {
        var weights=new ArrayList<String>();var sum=BigInteger.ZERO;
        for(int i=1;i<route.size();i++){long weight=p.cost(route.get(i-1),route.get(i));weights.add(Long.toString(weight));sum=sum.add(BigInteger.valueOf(weight));}
        System.out.println("  "+String.join(" + ",weights)+" = "+sum);
    }
}
