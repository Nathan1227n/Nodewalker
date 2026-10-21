package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathResult;
import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import java.math.BigInteger;
import java.util.*;

/** Complete mathematical presentation, built from authoritative results, never filtered map edges. */
public final class CostBreakdown {
    public enum Kind { TSP_CYCLE, SHORTEST_PATH }
    public record Term(int step, int from, int to, long weight, BigInteger accumulated) {
        public String connection() { return from + " → " + to; }
    }
    private final Kind kind;
    private final int origin, destination;
    private final List<Term> terms;
    private final Optional<BigInteger> total;
    private final String conclusion;

    private CostBreakdown(Kind kind, int origin, int destination, List<Term> terms, Optional<BigInteger> total, String conclusion) {
        this.kind=kind; this.origin=origin; this.destination=destination; this.terms=List.copyOf(terms); this.total=total; this.conclusion=conclusion;
    }
    public static CostBreakdown tsp(TspProblem problem, Optional<TspResult> result) {
        Objects.requireNonNull(problem); Objects.requireNonNull(result);
        if(result.isEmpty() || result.orElseThrow().route().isEmpty())
            return new CostBreakdown(Kind.TSP_CYCLE,0,0,List.of(),Optional.empty(),"Sem ciclo incumbente; nenhum custo de solução disponível.");
        var r=result.orElseThrow(); var route=r.route().orElseThrow();
        if(route.cityCount()!=problem.size()) throw new IllegalArgumentException("Different TSP instance size");
        return build(Kind.TSP_CYCLE,problem,route.indices(),BigInteger.valueOf(r.cost().orElseThrow()),
                r.optimalityGuaranteed()?"Ótimo certificado · soma conferida com TspResult.cost()."
                        :"Incumbente não certificada · soma conferida com TspResult.cost(); não prova ótimo.");
    }
    public static CostBreakdown shortestPath(RefugeGraph graph,int origin,int destination,Optional<ShortestPathResult> result) {
        Objects.requireNonNull(graph); Objects.requireNonNull(result); Objects.checkIndex(origin,graph.size()); Objects.checkIndex(destination,graph.size());
        if(result.isEmpty()) return new CostBreakdown(Kind.SHORTEST_PATH,origin,destination,List.of(),Optional.empty(),"Sem resultado de caminhos mínimos.");
        var r=result.orElseThrow();
        if(r.graph()!=graph || r.origin()!=origin) throw new IllegalArgumentException("Different shortest-path graph/origin");
        var path=r.pathTo(destination);
        if(path.isEmpty()) return new CostBreakdown(Kind.SHORTEST_PATH,origin,destination,List.of(),Optional.empty(),
                r.vertexState(destination)==ShortestPathResult.VertexState.INALCANCAVEL?"Destino inalcançável; não existe soma de caminho."
                        :"Destino não resolvido; nenhuma soma confirmada disponível.");
        var indices=path.orElseThrow();
        if(indices.getFirst()!=origin || indices.getLast()!=destination) throw new IllegalArgumentException("Invalid shortest-path endpoints");
        for(int i=1;i<indices.size();i++) if(!graph.topology().hasEdge(indices.get(i-1),indices.get(i)))
            throw new IllegalArgumentException("Path uses unavailable connection");
        return build(Kind.SHORTEST_PATH,graph.problem(),indices,r.distanceTo(destination).orElseThrow(),
                "Distância confirmada · soma conferida com distanceTo("+destination+"). Caminho mínimo, não ciclo TSP.");
    }
    private static CostBreakdown build(Kind kind,TspProblem problem,List<Integer> indices,BigInteger expected,String conclusion) {
        var terms=new ArrayList<Term>(); var sum=BigInteger.ZERO;
        for(int i=1;i<indices.size();i++) {
            int from=indices.get(i-1),to=indices.get(i); long weight=problem.cost(from,to);
            sum=sum.add(BigInteger.valueOf(weight)); terms.add(new Term(i,from,to,weight,sum));
        }
        if(!sum.equals(expected)) throw new IllegalArgumentException("Cost decomposition differs from authoritative result: "+sum+" != "+expected);
        return new CostBreakdown(kind,indices.getFirst(),indices.getLast(),terms,Optional.of(sum),conclusion);
    }
    /** O(1) access to data already prepared on a worker. A pending scene has no previous breakdown. */
    public static Optional<CostBreakdown> ofScene(WorldSceneModel scene) {
        if(scene instanceof TspSceneModel tsp) return Optional.of(tsp.costBreakdown());
        if(scene instanceof ShortestPathSceneModel paths) return Optional.of(paths.costBreakdown());
        return Optional.empty();
    }
    public Kind kind(){return kind;}
    public int origin(){return origin;}
    public int destination(){return destination;}
    public List<Term> terms(){return terms;}
    public Optional<BigInteger> total(){return total;}
    public String conclusion(){return conclusion;}
    public Optional<BigInteger> accumulated(int completedSegments) {
        if(total.isEmpty()) return Optional.empty();
        Objects.checkIndex(completedSegments,terms.size()+1);
        return Optional.of(completedSegments==0?BigInteger.ZERO:terms.get(completedSegments-1).accumulated());
    }
    public String expression() {
        if(total.isEmpty()) return "Soma confirmada: não disponível";
        return (terms.isEmpty()?"0":String.join(" + ",terms.stream().map(t->Long.toString(t.weight())).toList()))+" = "+total.orElseThrow();
    }
    public String replayText(int completedSegments) {
        return total.isEmpty()?"": "Reprodução do resultado calculado · acumulado de "+completedSegments+"/"+terms.size()
                +" ligações: "+accumulated(completedSegments).orElseThrow()+" / "+total.orElseThrow();
    }
    public String copyText() {
        var out=new StringBuilder(kind==Kind.TSP_CYCLE?"Decomposição do ciclo TSP (inclui retorno)":"Decomposição do caminho mínimo");
        out.append("\nOrigem: ").append(origin).append(" · destino: ").append(destination).append('\n').append(conclusion).append('\n');
        for(var t:terms) out.append(t.step()).append(". ").append(t.connection()).append(" · peso ").append(t.weight()).append(" · acumulado ").append(t.accumulated()).append('\n');
        return out.append(expression()).toString();
    }
}
