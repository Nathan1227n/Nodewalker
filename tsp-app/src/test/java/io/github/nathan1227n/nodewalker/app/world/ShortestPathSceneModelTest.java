package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.generator.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShortestPathSceneModelTest {
    @Test void exactIntermediateEdgesAndMatrixCostsInBothProjectionsWithoutReturnToOrigin() {
        var p=TspGenerator.generate(5,23,new GenerationOptions(10000,1000000)); var world=PreparedWorld.prepare(p);
        var g=new RefugeGraph(p,RefugeTopology.manual(5,List.of(new RefugeTopology.Edge(0,2),new RefugeTopology.Edge(2,4),new RefugeTopology.Edge(1,3))));
        var r=new DijkstraShortestPathSolver().solve(g); var m=new ShortestPathSceneModel(world,g,0,4,Optional.of(r));
        assertEquals(List.of(2,4),m.segments().stream().map(WorldSceneModel.Edge::to).toList());
        var sum=java.math.BigInteger.ZERO;
        for(var e:m.segments()) {
            assertEquals(p.cost(e.from(),e.to()),e.matrixCost()); sum=sum.add(java.math.BigInteger.valueOf(e.matrixCost()));
            assertEquals(world.positions().get(e.from()),e.geometry().start()); assertEquals(world.positions().get(e.to()),e.geometry().end());
            assertEquals(world.projection().project(e.geometry().start(),800,600,92),m.projection().project(world.positions().get(e.from()),800,600,92));
        }
        assertEquals(r.distanceTo(4).orElseThrow(),sum);
        assertTrue(new ShortestPathSceneModel(world,g,0,0,Optional.of(r)).segments().isEmpty());
        assertTrue(new ShortestPathSceneModel(world,g,0,3,Optional.of(r)).segments().isEmpty());
    }
    @Test void navigationAndGeometrySurviveProblemAndDestinationChanges() {
        var p=TspGenerator.generate(5,23,new GenerationOptions(10000,1000000)); var tsp=TspSceneModel.of(p,Optional.empty());
        var g=new RefugeGraph(p,RefugeTopology.complete(5)); var r=new DijkstraShortestPathSolver().solve(g);
        var state=new WorldViewState(); state.setModel(tsp); state.select(3); state.camera().orbit(15,8); var camera=state.camera(); var pose=camera.pose(800,600);
        var path=new ShortestPathSceneModel(tsp.preparedWorld(),g,0,2,Optional.of(r)); state.setModel(path);
        assertSame(camera,state.camera()); assertEquals(pose,state.camera().pose(800,600)); assertEquals(3,state.selected()); assertSame(tsp.geometry(false),path.geometry(false));
        var other=new ShortestPathSceneModel(tsp.preparedWorld(),g,0,4,Optional.of(r)); state.setModel(other); assertSame(camera,state.camera());
        assertSame(path.preparedWorld().buffers(path.terrain(false)),other.preparedWorld().buffers(other.terrain(false)));
    }
    @Test void seededSparseConnectionsUseOnlyOriginalCostsAndNeverMutateMatrix() {
        var p=TspGenerator.generate(8,23,new GenerationOptions(10000,1000000)); var w=PreparedWorld.prepare(p); var a=RefugeTopologyGenerator.connectedSparse(8,44,3); var b=RefugeTopologyGenerator.connectedSparse(8,44,3);
        var ma=new ShortestPathSceneModel(w,new RefugeGraph(p,a),0,7,Optional.empty()); var mb=new ShortestPathSceneModel(w,new RefugeGraph(p,b),0,7,Optional.empty());
        assertEquals(ma.secondaryConnections(0),mb.secondaryConnections(0)); assertEquals(10,ma.secondaryConnections(0).size());
        for(var e:ma.secondaryConnections(0)) { assertTrue(a.hasEdge(e.from(),e.to())); assertEquals(p.cost(e.from(),e.to()),e.matrixCost()); }
        var again=TspGenerator.generate(8,23,new GenerationOptions(10000,1000000));
        for(int i=0;i<8;i++)for(int j=0;j<8;j++)assertEquals(again.cost(i,j),p.cost(i,j));
    }
    @Test void completeVisualFilterDoesNotChangeSolverGraphOrPath() {
        var p=TspGenerator.generate(40,23,new GenerationOptions(10000,1000000)); var w=PreparedWorld.prepare(p); var g=new RefugeGraph(p,RefugeTopology.complete(40)); var r=new DijkstraShortestPathSolver().solve(g);
        var m=new ShortestPathSceneModel(w,g,0,39,Optional.of(r));
        assertTrue(m.connectionLabel().contains("filtro visual")); assertTrue(m.secondaryConnections(0).size()<=256);
        assertEquals(780,g.topology().edgeCount()); assertEquals(r.pathTo(39).orElseThrow().size()-1,m.segments().size());
    }
    @Test void meshBuffersAreDefensiveAndPriorityLabelsIncludeSourceDestination() {
        var p=TspGenerator.generate(3,23,new GenerationOptions(10000,1000000)); var w=PreparedWorld.prepare(p); var data=w.buffers(w.geometry(false).terrain());
        float original=data.points()[0]; var values=data.points(); values[0]=12345; assertEquals(original,data.points()[0]);
        var m=new ShortestPathSceneModel(w,new RefugeGraph(p,RefugeTopology.complete(3)),1,2,Optional.empty());
        assertEquals(Set.of(0,1,2),m.labelPriorities()); assertTrue(m.vertexTag(1).contains("[O]")); assertTrue(m.vertexTag(2).contains("[D]"));
        var requests=List.of(new LabelLayout.Request(0,new Vec3(0,0,1),80,20,true),new LabelLayout.Request(1,new Vec3(50,50,1),80,20,true),new LabelLayout.Request(2,new Vec3(100,100,1),80,20,true));
        assertEquals(3,LabelLayout.arrange(requests,0,new LabelLayout.Rect(0,0,500,500),true,m.labelPriorities()).size());
    }
    @Test void mismatchedInstanceOrOriginCannotBePresented() {
        var p=TspGenerator.generate(3,23,new GenerationOptions(10000,1000000)); var w=PreparedWorld.prepare(p); var g=new RefugeGraph(p,RefugeTopology.complete(3)); var r=new DijkstraShortestPathSolver().solve(g);
        assertThrows(IllegalArgumentException.class,()->new ShortestPathSceneModel(w,g,1,2,Optional.of(r)));
        var other=TspGenerator.generate(3,23,new GenerationOptions(10000,1000000));
        assertThrows(IllegalArgumentException.class,()->new ShortestPathSceneModel(w,new RefugeGraph(other,g.topology()),0,2,Optional.empty()));
    }
}
