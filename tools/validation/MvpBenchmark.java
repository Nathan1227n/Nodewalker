import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

/** Fixed, bounded validation experiment; no UI, no production API changes, no performance assertions. */
public class MvpBenchmark {
    static final int WARMUP=2, RUNS=5;
    static final long BATTERY_NANOS=60_000_000_000L;
    static final SolverExecutionOptions TSP=SolverExecutionOptions.limits(1_000_000,2000);
    static final ShortestPathExecutionOptions PATHS=ShortestPathExecutionOptions.demonstration();
    static long start;
    public static void main(String[] args) throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("No arguments; fixed audit grid");
        start=System.nanoTime();
        log("type","environment","java",System.getProperty("java.version"),"os",System.getProperty("os.name"),
                "arch",System.getProperty("os.arch"),"warmup",WARMUP,"runs",RUNS,"batterySeconds",60,
                "tspNodes",1_000_000,"tspMillis",2000,"pathVertices",PATHS.maxProcessedVertices(),
                "pathEntries",PATHS.maxExaminedEdges(),"pathMillis",PATHS.maxElapsedMillis(),"N10CyclesPerBruteCall",362880);
        try {
            int instance=0;
            for(int n:new int[]{3,5,8,10})for(long seed:new long[]{0,1,657}) {
                budget();long before=System.nanoTime();var p=TspGenerator.generate(n,seed,new GenerationOptions(100_000,64L*1024*1024));
                long generated=System.nanoTime()-before;String hash=hash(p);int id=++instance;TspResult common=null;
                for(SolverAlgorithm algorithm:SolverAlgorithm.values()) {
                    var solver=algorithm==SolverAlgorithm.BRUTE_FORCE?(TspSolver)new BruteForceSolver():new BacktrackingSolver();
                    log("type","configuration","problem","TSP","instance",id,"N",n,"seed",seed,"algorithm",algorithm.name(),
                            "matrixSha256",hash,"generationNanos",generated,"sameObjectAcrossAlgorithms",true);
                    var times=new ArrayList<Long>();boolean completed=true;TspResult reference=null;
                    for(int i=-WARMUP;i<RUNS;i++) {
                        budget();var r=solver.solve(p,TSP,new SolverCancellationToken());
                        completed&=r.status()==SolverStatus.CONCLUIDO;
                        if(r.status()==SolverStatus.CONCLUIDO) {
                            if(reference!=null && (!reference.cost().equals(r.cost()) || !reference.route().equals(r.route())))throw new AssertionError("TSP nondeterminism");
                            if(common!=null && (!common.cost().equals(r.cost()) || !common.route().equals(r.route())))throw new AssertionError("TSP algorithms disagree");
                            reference=r;common=r;
                        }
                        if(i>=0 && r.metrics().elapsedNanos().isPresent())times.add(r.metrics().elapsedNanos().orElseThrow());
                        log("type","sample","problem","TSP","instance",id,"algorithm",algorithm.name(),"phase",i<0?"warmup":"measured", "index",i<0?i+WARMUP:i,
                                "status",r.status().name(),"internalNanos",optional(r.metrics().elapsedNanos()),"cost",optional(r.cost()),
                                "route",r.route().map(v->v.indices()).orElse(List.of()),"nodes",optional(r.metrics().visitedNodes()),
                                "evaluated",optional(r.metrics().completeRoutesEvaluated()),"discarded",r.metrics().discardedRoutes().map(Object::toString).orElse("NA"));
                    }
                    summary("TSP",id,algorithm.name(),completed,times);
                    if(!hash.equals(hash(p)))throw new AssertionError("Matrix changed");
                }
            }
            for(int n:new int[]{8,32,128})for(long seed:new long[]{0,657}) {
                budget();long before=System.nanoTime();var p=TspGenerator.generate(n,seed,new GenerationOptions(100_000,64L*1024*1024));
                long generated=System.nanoTime()-before;int id=++instance;String hash=hash(p);
                for(boolean sparse:new boolean[]{false,true}) {
                    long topologyStart=System.nanoTime();var top=sparse?RefugeTopologyGenerator.connectedSparse(n,seed,n/2):RefugeTopology.complete(n);
                    var graph=new RefugeGraph(p,top);long topologyNanos=System.nanoTime()-topologyStart;
                    log("type","configuration","problem","SHORTEST_PATH","instance",id,"N",n,"seed",seed,"topology",top.kind().name(),
                            "topologySeed",seed,"extras",sparse?n/2:0,"edges",top.edgeCount(),"origin",0,"destination",n-1,
                            "matrixSha256",hash,"generationNanos",generated,"topologyNanos",topologyNanos);
                    var times=new ArrayList<Long>();boolean completed=true;ShortestPathResult reference=null;
                    for(int i=-WARMUP;i<RUNS;i++) {
                        budget();var r=new DijkstraShortestPathSolver().solve(graph,0,PATHS,new ShortestPathCancellationToken());
                        completed&=r.status()==ShortestPathStatus.CONCLUIDO;
                        if(r.status()==ShortestPathStatus.CONCLUIDO) {
                            if(reference!=null)for(int v=0;v<n;v++)if(!reference.distanceTo(v).equals(r.distanceTo(v)) || !reference.pathTo(v).equals(r.pathTo(v)))throw new AssertionError("Dijkstra nondeterminism");
                            reference=r;
                        }
                        if(i>=0 && r.metrics().elapsedNanos().isPresent())times.add(r.metrics().elapsedNanos().orElseThrow());
                        var m=r.metrics();
                        log("type","sample","problem","SHORTEST_PATH","instance",id,"topology",top.kind().name(),"phase",i<0?"warmup":"measured","index",i<0?i+WARMUP:i,
                                "status",r.status().name(),"internalNanos",optional(m.elapsedNanos()),"distance",r.distanceTo(n-1).map(Object::toString).orElse("NA"),
                                "path",r.pathTo(n-1).orElse(List.of()),"vertices",m.processedVertices(),"entries",m.examinedEdges(),
                                "attempts",m.relaxationAttempts(),"improved",m.successfulRelaxations(),"peakQueue",m.peakQueueSize());
                    }
                    summary("SHORTEST_PATH",id,top.kind().name(),completed,times);
                    if(!hash.equals(hash(p)))throw new AssertionError("Matrix changed");
                }
            }
            log("type","battery","status","CONCLUIDO","wallNanos",System.nanoTime()-start);
        } catch(BatteryLimit limit) {log("type","battery","status","LIMITE_ATINGIDO","reason","60 second global audit budget; remaining configurations not measured");}
    }
    static Object optional(OptionalLong value){return value.isPresent()?value.getAsLong():"NA";}
    static void summary(String problem,int id,String algorithm,boolean complete,List<Long> times) {
        var sorted=new ArrayList<>(times);Collections.sort(sorted);
        log("type","summary","problem",problem,"instance",id,"algorithmOrTopology",algorithm,
                "allSevenCallsCompleted",complete,"measuredSamples",times,
                "medianInternalNanos",complete && sorted.size()==RUNS?sorted.get(RUNS/2):"NA");
    }
    static void budget(){if(System.nanoTime()-start>=BATTERY_NANOS)throw new BatteryLimit();}
    static final class BatteryLimit extends RuntimeException{}
    static String hash(TspProblem p)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");var bytes=ByteBuffer.allocate(Long.BYTES);
        for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++){bytes.clear();bytes.putLong(p.cost(i,j));digest.update(bytes.array());}
        return HexFormat.of().formatHex(digest.digest());
    }
    static void log(Object... values){var map=new LinkedHashMap<String,Object>();for(int i=0;i<values.length;i+=2)map.put((String)values[i],values[i+1]);System.out.println(json(map));}
    static String json(Object o) {
        if(o instanceof Number || o instanceof Boolean)return o.toString();
        if(o instanceof List<?> a)return "["+String.join(",",a.stream().map(MvpBenchmark::json).toList())+"]";
        if(o instanceof Map<?,?> m)return "{"+String.join(",",m.entrySet().stream().map(e->json(e.getKey())+":"+json(e.getValue())).toList())+"}";
        return "\""+o.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
    }
}
