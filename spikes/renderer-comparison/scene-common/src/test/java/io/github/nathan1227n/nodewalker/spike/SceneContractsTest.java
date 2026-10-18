package io.github.nathan1227n.nodewalker.spike;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class SceneContractsTest {
    @Test void snapshotHasClosedPermutationAndStoredCost() {
        assertEquals(5,SceneSnapshot.cities().size());
        assertEquals(List.of(0,1,2,3,4,0),SceneSnapshot.cycle());
        assertEquals(Set.of(0,1,2,3,4),new HashSet<>(SceneSnapshot.cycle().subList(0,5)));
        assertEquals(2338,SceneSnapshot.cycleCost());
        for(int i=0;i<5;i++) for(int j=0;j<5;j++) {
            assertEquals(SceneSnapshot.cost(i,j),SceneSnapshot.cost(j,i));
            assertEquals(Math.round(SceneSnapshot.cities().get(i).subtract(SceneSnapshot.cities().get(j)).length()),SceneSnapshot.cost(i,j));
        }
    }
    @Test void snapshotCannotBeChangedByRenderers() {
        assertThrows(UnsupportedOperationException.class,()->SceneSnapshot.cities().clear());
        assertThrows(UnsupportedOperationException.class,()->SceneSnapshot.cycle().set(1,4));
        long[][] matrix=SceneSnapshot.matrix();matrix[0][1]=99;
        assertEquals(568,SceneSnapshot.cost(0,1));
    }
    @ParameterizedTest @EnumSource(DisplayTransform.class)
    void mappingHasExplicitInverseAndPreservesLengths(DisplayTransform transform) {
        for(Vec3 p:SceneSnapshot.cities()) {
            assertEquals(p,transform.inverse(transform.forward(p)));
            for(Vec3 q:SceneSnapshot.cities()) assertEquals(p.subtract(q).length(),transform.forward(p).subtract(transform.forward(q)).length(),1e-9);
        }
        Vec3 logical=new Vec3(0,100,1000),v=transform.forward(logical);
        assertEquals(-500,v.x());assertEquals(transform==DisplayTransform.JAVAFX?-500:500,v.z());
        assertEquals(transform==DisplayTransform.JAVAFX?-100:100,v.y());
        assertFalse(transform.reversesWinding());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void terrainIsDeterministicFiniteAndFacesUp(boolean simple) {
        var mesh=SceneGeometry.terrain(simple);
        assertEquals(simple?2048:8192,mesh.size());assertEquals(mesh,SceneGeometry.terrain(simple));
        for(var t:mesh) {
            Vec3 b=t.b().subtract(t.a()),c=t.c().subtract(t.a());
            assertTrue(b.z()*c.x()-b.x()*c.z()>0,"+Y normal");
            for(Vec3 v:List.of(t.a(),t.b(),t.c())) assertTrue(Double.isFinite(v.y()));
            assertTrue(t.band()>=0 && t.band()<SceneGeometry.ROCK.length);
        }
    }
    @Test void beaconAnchorsAreLogicalCitiesAndDecorationDoesNotMoveThem() {
        var beacons=SceneGeometry.shelters().stream().filter(p->p.shape()==SceneGeometry.Shape.SPHERE).toList();
        assertEquals(5,beacons.size());
        for(int i=0;i<5;i++) {
            assertEquals(SceneSnapshot.cities().get(i),beacons.get(i).position());
            assertEquals(i,beacons.get(i).owner());
            assertEquals(i==0?SceneGeometry.AMBER:SceneGeometry.CYAN,beacons.get(i).color());
            Vec3 city=SceneSnapshot.cities().get(i);assertEquals(city.y()-48,SceneGeometry.height(city.x(),city.z()),1e-9);
        }
        assertTrue(SceneGeometry.decoration().stream().allMatch(p->p.owner()==-1));
    }
    @Test void cameraResetAndBenchmarkAreDeterministicAndZoomClamped() {
        OrbitCamera a=new OrbitCamera(),b=new OrbitCamera();Vec3 initial=a.position();
        a.orbit(400,100000);a.zoom(100000);assertEquals(650,a.position().subtract(OrbitCamera.TARGET).length(),1e-8);
        a.zoom(-100000);assertEquals(3200,a.position().subtract(OrbitCamera.TARGET).length(),1e-8);
        a.reset();assertEquals(initial,a.position());
        a.benchmark(12.5);b.benchmark(12.5);assertEquals(a.position(),b.position());
    }
}
