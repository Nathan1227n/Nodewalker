package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CostBreakdownTest {
    static TspProblem triangle(long a,long b,long c){return new TspProblem(List.of(new City(0,0,0,0),new City(1,20,400,100),new City(2,200,10,500)),new long[][]{{0,a,c},{a,0,b},{c,b,0}});}
    @ParameterizedTest @EnumSource(value=SolverStatus.class,names={"CONCLUIDO","CANCELADO","LIMITE_ATINGIDO","ERRO"})
    void fullIncumbentAlwaysHasEveryTermIncludingReturnWithoutInventingOptimality(SolverStatus status) {
        var p=triangle(2,3,4);var route=new TspRoute(List.of(0,1,2,0));
        var r=status==SolverStatus.CONCLUIDO?TspResult.completed(p,route,9,SolverMetrics.empty()):TspResult.interrupted(p,status,Optional.of(route),OptionalLong.of(9),SolverMetrics.empty(),"interrompido");
        var b=CostBreakdown.tsp(p,Optional.of(r));
        assertEquals(3,b.terms().size());assertEquals(List.of("0 → 1","1 → 2","2 → 0"),b.terms().stream().map(CostBreakdown.Term::connection).toList());
        assertEquals("2 + 3 + 4 = 9",b.expression());assertEquals(BigInteger.valueOf(9),b.total().orElseThrow());
        assertTrue(b.conclusion().contains(status==SolverStatus.CONCLUIDO?"Ótimo certificado":"Incumbente não certificada"));
        assertTrue(b.copyText().contains("3. 2 → 0 · peso 4 · acumulado 9"));
    }
    @ParameterizedTest @EnumSource(value=SolverStatus.class,names={"CANCELADO","LIMITE_ATINGIDO","ERRO"})
    void noIncumbentMeansNoNumericTotal(SolverStatus status) {
        var p=triangle(2,3,4);var r=TspResult.interrupted(p,status,Optional.empty(),OptionalLong.empty(),SolverMetrics.empty(),"sem incumbente");
        var b=CostBreakdown.tsp(p,Optional.of(r));assertTrue(b.terms().isEmpty());assertTrue(b.total().isEmpty());assertTrue(b.accumulated(0).isEmpty());
        assertFalse(b.expression().contains("= 0"));assertEquals("",b.replayText(0));
    }
    @ParameterizedTest @ValueSource(longs={0,Long.MAX_VALUE})
    void exactIntegerExtremesAreNotAbbreviatedOrUsedAsAbsence(long cost) {
        var p=triangle(cost,0,0);var r=new BruteForceSolver().solve(p);var b=CostBreakdown.tsp(p,Optional.of(r));
        assertEquals(BigInteger.valueOf(cost),b.total().orElseThrow());assertEquals(3,b.terms().size());
        assertTrue(b.expression().startsWith(Long.toString(cost)+" + 0 + 0 = "));assertTrue(b.copyText().contains(Long.toString(cost)));
    }
    @Test void dijkstraUsesBigIntegerAboveLongAndNeverClosesItsPath() {
        var p=triangle(Long.MAX_VALUE,Long.MAX_VALUE,0);var g=new RefugeGraph(p,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1),new RefugeTopology.Edge(1,2))));
        var r=new DijkstraShortestPathSolver().solve(g);var b=CostBreakdown.shortestPath(g,0,2,Optional.of(r));
        var total=BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        assertEquals(total,b.total().orElseThrow());assertEquals(2,b.terms().size());assertEquals(2,b.terms().getLast().to());assertEquals(total,b.accumulated(2).orElseThrow());
        assertEquals(r.distanceTo(2),b.total());assertTrue(b.copyText().contains(total.toString()));assertFalse(b.conclusion().contains("Ótimo certificado"));
    }
    @Test void confirmedOriginIsZeroWhileUnreachableHasNoNumericSum() {
        var p=triangle(0,8,3);var g=new RefugeGraph(p,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1))));var r=new DijkstraShortestPathSolver().solve(g);
        var origin=CostBreakdown.shortestPath(g,0,0,Optional.of(r));assertTrue(origin.terms().isEmpty());assertEquals(BigInteger.ZERO,origin.total().orElseThrow());assertEquals("0 = 0",origin.expression());
        var zero=CostBreakdown.shortestPath(g,0,1,Optional.of(r));assertEquals(1,zero.terms().size());assertEquals(0,zero.terms().getFirst().weight());
        var missing=CostBreakdown.shortestPath(g,0,2,Optional.of(r));assertTrue(missing.total().isEmpty());assertTrue(missing.conclusion().contains("inalcançável"));
    }
    @Test void limitedDijkstraKeepsOnlyConfirmedBreakdowns() {
        var g=new RefugeGraph(triangle(2,3,20),RefugeTopology.complete(3));
        var r=new DijkstraShortestPathSolver().solve(g,ShortestPathExecutionOptions.limits(2,100,5000),new ShortestPathCancellationToken());
        assertEquals(ShortestPathStatus.LIMITE_ATINGIDO,r.status());assertEquals(BigInteger.valueOf(2),CostBreakdown.shortestPath(g,0,1,Optional.of(r)).total().orElseThrow());
        var tentative=CostBreakdown.shortestPath(g,0,2,Optional.of(r));assertTrue(tentative.total().isEmpty());assertTrue(tentative.conclusion().contains("não resolvido"));
    }
    @Test void cooperativelyCancelledDijkstraPreservesConfirmedCostAndRejectsTentativeCost() throws Exception {
        var g=new RefugeGraph(triangle(2,3,20),RefugeTopology.complete(3));var token=new ShortestPathCancellationToken();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try {
            var future=executor.submit(()->ControlledDijkstra.afterTwoVertices(entered,release).solve(g,0,ShortestPathExecutionOptions.demonstration(),token));
            assertTrue(entered.await(5,TimeUnit.SECONDS));token.cancel();release.countDown();var r=future.get(5,TimeUnit.SECONDS);
            assertEquals(ShortestPathStatus.CANCELADO,r.status());assertEquals(BigInteger.valueOf(2),CostBreakdown.shortestPath(g,0,1,Optional.of(r)).total().orElseThrow());
            assertTrue(CostBreakdown.shortestPath(g,0,2,Optional.of(r)).total().isEmpty());
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void decompositionRejectsWrongMatrixOrDifferentGraphRatherThanConfirmingFalseSum() {
        var p=triangle(2,3,4);var r=new BruteForceSolver().solve(p);
        assertThrows(IllegalArgumentException.class,()->CostBreakdown.tsp(triangle(2,3,5),Optional.of(r)));
        var g=new RefugeGraph(p,RefugeTopology.complete(3));var paths=new DijkstraShortestPathSolver().solve(g);
        assertThrows(IllegalArgumentException.class,()->CostBreakdown.shortestPath(new RefugeGraph(p,g.topology()),0,2,Optional.of(paths)));
    }
    @Test void replayAddsWholeWeightsOnlyAndCannotInvokeAnySolver() {
        var p=triangle(2,3,4);var r=new BruteForceSolver().solve(p);var scene=TspSceneModel.of(p,Optional.of(r));var state=new WorldViewState();state.setModel(scene);
        state.playback().revealCities();var b=scene.costBreakdown();assertEquals(BigInteger.ZERO,b.accumulated(state.playback().completedEdges()).orElseThrow());
        state.playback().advance(799_999_999);assertEquals(BigInteger.ZERO,b.accumulated(state.playback().completedEdges()).orElseThrow());
        state.playback().advance(1);assertEquals(BigInteger.valueOf(2),b.accumulated(state.playback().completedEdges()).orElseThrow());
        state.playback().togglePause();state.playback().advance(10_000_000_000L);assertEquals(BigInteger.valueOf(2),b.accumulated(state.playback().completedEdges()).orElseThrow());
        state.playback().finish();assertEquals(b.total(),b.accumulated(3));state.playback().replay();assertEquals(BigInteger.ZERO,b.accumulated(0).orElseThrow());
        assertTrue(b.replayText(0).startsWith("Reprodução do resultado calculado"));assertSame(r,scene.result().orElseThrow());
    }
}
