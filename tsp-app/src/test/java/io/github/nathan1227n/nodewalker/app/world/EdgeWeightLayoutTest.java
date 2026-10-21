package io.github.nathan1227n.nodewalker.app.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EdgeWeightLayoutTest {
    static EdgeWeightPolicy.WeightedEdge edge(int order){return new EdgeWeightPolicy.WeightedEdge(0,order+2,Long.MAX_VALUE,new SegmentGeometry(Vec3.ZERO,new Vec3(1,1,1)),order);}
    @Test void refugeObstaclesHavePriorityAndSolutionCostsWinSecondaryCollisions() {
        var view=new LabelLayout.Rect(0,0,800,500);var obstacle=new LabelLayout.Rect(380,210,100,40);
        var requests=List.of(new EdgeWeightLayout.Request(edge(-1),new Vec3(400,250,10),160,24,100),new EdgeWeightLayout.Request(edge(0),new Vec3(400,250,10),160,24,100));
        var placements=EdgeWeightLayout.arrange(requests,view,List.of(obstacle),1,1000);
        assertTrue(placements.getFirst().edge().solution());
        for(var p:placements){assertTrue(view.contains(p.bounds()));assertFalse(p.bounds().overlaps(obstacle,5));}
        for(int i=0;i<placements.size();i++)for(int j=0;j<i;j++)assertFalse(placements.get(i).bounds().overlaps(placements.get(j).bounds(),5));
    }
    @Test void projectedMidpointInsideViewportDoesNotOverrideClippingOrFullTextBounds() {
        var view=new LabelLayout.Rect(0,0,300,200);
        for(var point:List.of(new Vec3(150,100,-1),new Vec3(150,100,1),new Vec3(150,100,1001),new Vec3(-1,100,10),new Vec3(301,100,10)))
            assertTrue(EdgeWeightLayout.arrange(List.of(new EdgeWeightLayout.Request(edge(0),point,160,24,100)),view,List.of(),1,1000).isEmpty());
        assertTrue(EdgeWeightLayout.arrange(List.of(new EdgeWeightLayout.Request(edge(0),new Vec3(150,100,10),301,24,100)),view,List.of(),1,1000).isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={320,700,1200})
    void densityAndResizeNeverProduceOverlapsOrOutOfBoundsLabels(int width) {
        var random=new Random(23);var requests=new ArrayList<EdgeWeightLayout.Request>();var view=new LabelLayout.Rect(8,30,width-16,500);
        for(int i=0;i<300;i++)requests.add(new EdgeWeightLayout.Request(edge(i<12?i:-1),new Vec3(random.nextDouble()*width,50+random.nextDouble()*450,10),160,24,100));
        var placements=EdgeWeightLayout.arrange(requests,view,List.of(),1,1000);assertTrue(placements.size()<=EdgeWeightLayout.MAX_LABELS);
        for(int i=0;i<placements.size();i++){assertTrue(view.contains(placements.get(i).bounds()));for(int j=0;j<i;j++)assertFalse(placements.get(i).bounds().overlaps(placements.get(j).bounds(),5));}
        assertEquals(placements,EdgeWeightLayout.arrange(requests,view,List.of(),1,1000));
    }
    @Test void distantShortProjectedSecondaryEdgesAreFilteredButRouteHasPriority() {
        var requests=List.of(new EdgeWeightLayout.Request(edge(-1),new Vec3(200,200,10),60,24,10),new EdgeWeightLayout.Request(edge(0),new Vec3(400,200,10),60,24,10));
        var placed=EdgeWeightLayout.arrange(requests,new LabelLayout.Rect(0,0,800,600),List.of(),1,1000);assertEquals(1,placed.size());assertTrue(placed.getFirst().edge().solution());
    }
    @Test void projectionUsesVisualMidpointAndCameraChangesWithoutTouchingLogicalWeight() {
        var e=edge(0);var camera=new OrbitCamera(List.of(new Vec3(0,0,0),new Vec3(300,100,300)),800);var pose=camera.pose(900,700);
        var anchor=pose.project(e.geometry().midpoint(),900,700);camera.orbit(30,10);camera.zoom(100);var changed=camera.pose(700,900).project(e.geometry().midpoint(),700,900);
        assertNotEquals(anchor,changed);assertEquals(Long.MAX_VALUE,e.matrixCost());assertEquals(new Vec3(.5,.5,.5),e.geometry().midpoint());
    }
}
