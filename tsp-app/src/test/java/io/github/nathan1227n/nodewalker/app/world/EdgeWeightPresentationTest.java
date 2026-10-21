package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class EdgeWeightPresentationTest {
    static Stream<org.junit.jupiter.params.provider.Arguments> cases(){return IntStream.of(5,8,12).boxed().flatMap(n->Stream.of(23L,657L).map(seed->Arguments.of(n,seed)));}
    @ParameterizedTest @MethodSource("cases")
    void tagsUseMatrixCostsAndBreakdownSurvivesAllVisualModes(int n,long seed) {
        var p=TspGenerator.generate(n,seed);var r=new BacktrackingSolver().solve(p,SolverExecutionOptions.nodeLimit(10000),new SolverCancellationToken());
        var scene=TspSceneModel.of(p,Optional.of(r));var state=new WorldViewState();state.setModel(scene);var b=scene.costBreakdown();
        assertEquals(n,b.terms().size());var before=new long[n][n];for(int i=0;i<n;i++)for(int j=0;j<n;j++)before[i][j]=p.cost(i,j);
        for(var mode:EdgeWeightMode.values())for(var scope:EdgeWeightPolicy.Scope.values()) {
            state.setWeightMode(mode);state.setWeightScope(scope);state.select(n-1);
            assertEquals((long)n*(n-1)/2,state.weightSelection().graphEdges().orElseThrow());
            for(var e:state.weightSelection().candidates())assertEquals(p.cost(e.from(),e.to()),e.matrixCost());
            assertEquals(mode==EdgeWeightMode.NONE?0:n,state.weightSelection().candidates().stream().filter(EdgeWeightPolicy.WeightedEdge::solution).count());
            assertSame(b,CostBreakdown.ofScene(state.model()).orElseThrow());assertEquals(n,b.terms().size());
        }
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)assertEquals(before[i][j],p.cost(i,j));
    }
    @ParameterizedTest @EnumSource(value=RefugeTopology.Kind.class,names={"COMPLETE","SPARSE_CONNECTED"})
    void dijkstraWeightsRespectActualTopologyAndIncidentScope(RefugeTopology.Kind kind) {
        var p=TspGenerator.generate(12,23);var top=kind==RefugeTopology.Kind.COMPLETE?RefugeTopology.complete(12):RefugeTopologyGenerator.connectedSparse(12,657,4);
        var graph=new RefugeGraph(p,top);var r=new DijkstraShortestPathSolver().solve(graph);var w=PreparedWorld.prepare(p);
        var scene=new ShortestPathSceneModel(w,graph,0,8,Optional.of(r));var state=new WorldViewState();state.setModel(scene);state.select(5);state.setWeightMode(EdgeWeightMode.CONNECTIONS);
        for(var scope:EdgeWeightPolicy.Scope.values()) {
            state.setWeightScope(scope);assertEquals(top.edgeCount(),state.weightSelection().graphEdges().orElseThrow());
            for(var e:state.weightSelection().candidates()) {assertTrue(top.hasEdge(e.from(),e.to()));assertEquals(graph.cost(e.from(),e.to()),e.matrixCost());
                if(scope==EdgeWeightPolicy.Scope.INCIDENT_SELECTED && !e.solution())assertTrue(e.from()==5 || e.to()==5);}
            assertEquals(r.pathTo(8).orElseThrow().size()-1,state.weightSelection().candidates().stream().filter(EdgeWeightPolicy.WeightedEdge::solution).count());
        }
        assertSame(graph,r.graph());assertEquals(r.distanceTo(8),scene.costBreakdown().total());
    }
    @Test void completeGraphIsNotEnumeratedIntoAnUnboundedOverlay() {
        var p=TspGenerator.generate(60,23);var g=new RefugeGraph(p,RefugeTopology.complete(60));var r=new DijkstraShortestPathSolver().solve(g);
        var scene=new ShortestPathSceneModel(PreparedWorld.prepare(p),g,0,59,Optional.of(r));var state=new WorldViewState();state.setModel(scene);state.setWeightMode(EdgeWeightMode.CONNECTIONS);
        assertEquals(1770,state.weightSelection().graphEdges().orElseThrow());assertTrue(state.secondaryConnections().size()<=256);
        assertTrue(state.weightSelection().candidates().size()<=256+scene.segments().size());
    }
    @Test void pendingSceneAndNewInstanceEraseCostsWhileNavigationSettingsSurvive() {
        var p=TspGenerator.generate(5,23);var scene=TspSceneModel.of(p,Optional.of(new BruteForceSolver().solve(p)));var state=new WorldViewState();state.setModel(scene);
        state.setWeightMode(EdgeWeightMode.CONNECTIONS);state.select(3);state.camera().orbit(12,8);var camera=state.camera();var pose=camera.pose(900,700);
        state.setModel(new PendingSceneModel(scene.preparedWorld()));
        assertTrue(state.weightSelection().candidates().isEmpty());assertTrue(CostBreakdown.ofScene(state.model()).isEmpty());assertTrue(state.weightSelection().graphEdges().isEmpty());
        assertEquals(3,state.selected());assertSame(camera,state.camera());assertEquals(pose,camera.pose(900,700));assertEquals(EdgeWeightMode.CONNECTIONS,state.weightMode());
        state.clear();assertTrue(state.weightSelection().candidates().isEmpty());assertTrue(CostBreakdown.ofScene(state.model()).isEmpty());
        var next=TspSceneModel.of(TspGenerator.generate(3,24),Optional.empty());state.setModel(next);assertTrue(next.costBreakdown().total().isEmpty());assertTrue(state.weightSelection().candidates().stream().noneMatch(EdgeWeightPolicy.WeightedEdge::solution));
    }
    @Test void destinationChangeKeepsSearchAndGeometryButReplacesTermsAndCandidates() {
        var p=CostBreakdownTest.triangle(2,3,20);var g=new RefugeGraph(p,RefugeTopology.complete(3));var r=new DijkstraShortestPathSolver().solve(g);var w=PreparedWorld.prepare(p);
        var a=new ShortestPathSceneModel(w,g,0,2,Optional.of(r));var b=new ShortestPathSceneModel(w,g,0,1,Optional.of(r));
        assertEquals(2,a.costBreakdown().terms().size());assertEquals(1,b.costBreakdown().terms().size());assertSame(a.result().orElseThrow(),b.result().orElseThrow());assertSame(a.preparedWorld(),b.preparedWorld());
        var state=new WorldViewState();state.setModel(a);state.setWeightMode(EdgeWeightMode.SOLUTION);assertEquals(2,state.weightSelection().candidates().size());state.setModel(b);assertEquals(1,state.weightSelection().candidates().size());
    }
    @Test void hidingSecondaryConnectionsDoesNotHideSolutionTermsOrChangeGraph() {
        var p=TspGenerator.generate(8,23);var g=new RefugeGraph(p,RefugeTopology.complete(8));var r=new DijkstraShortestPathSolver().solve(g);var scene=new ShortestPathSceneModel(PreparedWorld.prepare(p),g,0,7,Optional.of(r));
        var state=new WorldViewState();state.setModel(scene);state.setWeightMode(EdgeWeightMode.CONNECTIONS);state.setConnections(false);
        assertEquals(scene.segments().size(),state.weightSelection().candidates().size());assertEquals(28,state.weightSelection().graphEdges().orElseThrow());assertEquals(r.distanceTo(7),scene.costBreakdown().total());
    }
}
