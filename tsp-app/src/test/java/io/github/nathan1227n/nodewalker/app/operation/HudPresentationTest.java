package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HudPresentationTest {
    static TspProblem problem(long a, long b, long c) {
        return new TspProblem(List.of(new City(0,0,0,0),new City(1,1,2,3),new City(2,2,3,4)),new long[][]{{0,a,c},{a,0,b},{c,b,0}});
    }
    static OperationalSnapshot snapshot(ExecutionRecord record,int destination) {
        var p=record instanceof ExecutionRecord.Paths r ? r.result().graph().problem() : problem(0,0,0);
        return new OperationalSnapshot(OperationalSnapshot.Phase.valueOf(record.status()),"SOLVER",1,1,3,23,p,null,record.request(),destination,record,List.of(record),"","");
    }
    @Test void missingMetricsAreNotZeroAndMaximumLongIsExact() {
        var p=problem(Long.MAX_VALUE,0,0); var r=TspResult.completed(p,new TspRoute(List.of(0,1,2,0)),Long.MAX_VALUE,SolverMetrics.empty());
        var h=HudPresentation.tsp(new ExecutionRecord.Tsp(1,1,ExecutionRequest.defaults(),r));
        assertEquals("9223372036854775807",h.cost()); assertTrue(h.metrics().contains("Nós visitados: não informado"));
        assertTrue(h.metrics().contains("Folhas descartadas: não informado")); assertFalse(h.metrics().contains(": 0"));
    }
    @Test void bigIntegerDistanceAboveLongIsNotTruncated() {
        var p=problem(Long.MAX_VALUE,Long.MAX_VALUE,1);
        var g=new RefugeGraph(p,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1),new RefugeTopology.Edge(1,2))));
        var r=new DijkstraShortestPathSolver().solve(g); var request=OperationalControllerTest.request(ExecutionRequest.Algorithm.DIJKSTRA);
        var h=HudPresentation.of(snapshot(new ExecutionRecord.Paths(1,1,request,r),2));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).toString(),h.cost()); assertEquals("0 → 1 → 2",h.route());
        assertEquals("DISTÂNCIA CONFIRMADA",h.certification()); assertFalse(h.headline().contains("TSP"));
    }
    @Test void unresolvedAndUnreachableHaveNoDistanceOrInventedPath() {
        var p=problem(2,2,2); var g=new RefugeGraph(p,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1))));
        var req=OperationalControllerTest.request(ExecutionRequest.Algorithm.DIJKSTRA);
        var complete=new DijkstraShortestPathSolver().solve(g);
        var limited=new DijkstraShortestPathSolver().solve(g,ShortestPathExecutionOptions.limits(0,0,1000),new ShortestPathCancellationToken());
        assertTrue(HudPresentation.of(snapshot(new ExecutionRecord.Paths(1,1,req,complete),2)).headline().contains("INALCANCAVEL"));
        var h=HudPresentation.of(snapshot(new ExecutionRecord.Paths(1,1,req,limited),1));
        assertEquals("não informado",h.cost()); assertTrue(h.headline().contains("NAO_RESOLVIDO")); assertEquals("SEM DISTÂNCIA CONFIRMADA",h.certification());
    }
    @Test void partialConfirmedOriginCanBeDisplayedWithoutGlobalCompletion() {
        var p=problem(2,2,2); var g=new RefugeGraph(p,RefugeTopology.complete(3));
        var r=new DijkstraShortestPathSolver().solve(g,ShortestPathExecutionOptions.limits(1,0,1000),new ShortestPathCancellationToken());
        var h=HudPresentation.of(snapshot(new ExecutionRecord.Paths(1,1,OperationalControllerTest.request(ExecutionRequest.Algorithm.DIJKSTRA),r),0));
        assertEquals("LIMITE_ATINGIDO",h.status()); assertEquals("0",h.cost()); assertEquals("0",h.route()); assertEquals("DISTÂNCIA CONFIRMADA",h.certification());
    }
    @Test void generationFailureDoesNotInventASolverStatus() {
        var s=new OperationalSnapshot(OperationalSnapshot.Phase.ERRO,"GERAÇÃO",1,0,3,23,null,null,ExecutionRequest.defaults(),1,null,List.of(),"","generation failed");
        var h=HudPresentation.of(s); assertEquals("SEM RESULTADO DE SOLVER",h.status()); assertEquals("generation failed",h.reason());
    }
    @Test void panelRejectsUnlimitedTspOptions() {
        assertThrows(IllegalArgumentException.class,()->new ExecutionRequest(ExecutionRequest.Algorithm.BRUTE_FORCE,0,RefugeTopology.Kind.COMPLETE,23,0,SolverExecutionOptions.unlimited(),ShortestPathExecutionOptions.demonstration()));
    }
}
