package io.github.nathan1227n.nodewalker.app.world;

import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.nathan1227n.nodewalker.app.world.WorldPlayback.Stage.*;

class WorldPlaybackTest {
    @Test void citiesAppearGraduallyInsteadOfBeingImmediatelyComplete() {
        var clock = new WorldPlayback(); clock.newWorld(3);
        assertEquals(REFUGES, clock.stage()); assertEquals(0, clock.completedCities());
        assertEquals(0, clock.cityFraction(0));
        clock.advance(250_000_000); assertEquals(.5, clock.cityFraction(0), 1e-12);
        assertEquals(0, clock.cityFraction(1));
        clock.advance(250_000_000); assertEquals(1, clock.completedCities());
        clock.advance(1_000_000_000); assertEquals(FINISHED, clock.stage()); assertEquals(3, clock.completedCities());
    }
    @Test void pendingPathWaitsForRefugesAndConsumesOnlyRemainingTime() {
        var clock = new WorldPlayback(); clock.newWorld(3); clock.path(3);
        clock.advance(1_000_000_000); assertEquals(REFUGES, clock.stage()); assertEquals(0, clock.edgeFraction(0));
        clock.advance(900_000_000); assertEquals(PATH, clock.stage());
        assertEquals(.5, clock.edgeFraction(0), 1e-12); assertEquals(0, clock.edgeFraction(1));
    }
    @ParameterizedTest @ValueSource(ints={1,3,12,83})
    void completePlaybackHasExactEndpointsWithoutSkippingTheLastEdge(int count) {
        var clock = new WorldPlayback(); clock.newWorld(3); clock.revealCities(); clock.path(count);
        double seconds = Math.clamp(count * .8, 2.4, 10);
        clock.advance(Math.round(seconds * 1e9));
        assertEquals(FINISHED, clock.stage()); assertEquals(count, clock.completedEdges());
        for (int i=0;i<count;i++) assertEquals(1, clock.edgeFraction(i));
    }
    @Test void aSingleDirectDijkstraEdgeStillGetsReadablePlaybackTime() {
        var clock = new WorldPlayback(); clock.newWorld(3); clock.revealCities(); clock.path(1);
        clock.advance(500_000_000); assertTrue(clock.active()); assertTrue(clock.edgeFraction(0) < .25);
        assertTrue(clock.label().contains("caminho calculado"));
    }
    @Test void pauseAndResumeDoNotConsumePausedTime() {
        var clock = new WorldPlayback(); clock.newWorld(3); clock.revealCities(); clock.path(3);
        clock.advance(400_000_000); double fraction = clock.edgeFraction(0);
        clock.togglePause(); clock.advance(10_000_000_000L); assertEquals(fraction, clock.edgeFraction(0));
        assertTrue(clock.paused()); assertTrue(clock.label().startsWith("Pausado"));
        clock.togglePause(); clock.advance(400_000_000); assertEquals(1, clock.completedEdges());
    }
    @Test void speedAndReplayOnlyChangeThePresentationClock() {
        var clock = new WorldPlayback(); clock.newWorld(3); clock.revealCities(); clock.path(3); clock.speed(2);
        clock.advance(400_000_000); assertEquals(1, clock.completedEdges());
        clock.finish(); clock.replay(); assertEquals(PATH, clock.stage()); assertEquals(0, clock.completedEdges());
        assertEquals(3, clock.completedCities()); assertEquals(2, clock.speed());
        clock.speed(.5); clock.advance(400_000_000); assertEquals(.25, clock.edgeFraction(0), 1e-12);
    }
    @Test void newWorldAndEmptyPathInvalidateOldAnimationState() {
        var clock = new WorldPlayback(); clock.newWorld(3); clock.revealCities(); clock.path(3); clock.advance(1_000_000_000);
        clock.path(0); assertEquals(FINISHED, clock.stage()); assertEquals(0, clock.edgeCount());
        clock.newWorld(5); assertEquals(REFUGES, clock.stage()); assertEquals(0, clock.completedCities()); assertEquals(0, clock.edgeCount());
        clock.clear(); assertFalse(clock.active());
    }
    @Test void invalidTimesSpeedsAndIndicesAreRejected() {
        var clock = new WorldPlayback(); clock.newWorld(3);
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
        for(double speed:new double[]{0,.1,5,Double.NaN,Double.POSITIVE_INFINITY}) assertThrows(IllegalArgumentException.class, () -> clock.speed(speed));
        assertThrows(IndexOutOfBoundsException.class, () -> clock.cityFraction(3));
        assertThrows(IndexOutOfBoundsException.class, () -> clock.edgeFraction(0));
    }
    @Test void animationPreservesActualResultMatrixGeometryAndCamera() {
        var p = TspGenerator.generate(3,23,new GenerationOptions(10000,1000000));
        var r = new BruteForceSolver().solve(p); var m = TspSceneModel.of(p,Optional.of(r));
        var state = new WorldViewState(); state.setModel(m); state.playback().revealCities();
        var camera = state.camera(); var pose = camera.pose(800,600);
        state.playback().advance(400_000_000);
        var segment = m.segments().getFirst().geometry();
        assertEquals(segment.pointAt(.5), state.playbackCursor().orElseThrow());
        var projection = m.projection();
        assertEquals(projection.project(segment.pointAt(.5),800,600,92), projection.project(state.playbackCursor().orElseThrow(),800,600,92));
        state.playback().finish(); state.playback().replay(); state.playback().togglePause();
        assertSame(r, m.result().orElseThrow()); assertSame(p, m.problem()); assertEquals(r.cost().orElseThrow(), m.cost().orElseThrow());
        assertSame(camera, state.camera()); assertEquals(pose,camera.pose(800,600));
    }
    @Test void dijkstraPlaybackDoesNotCloseItsPathOrCertifyAnUnresolvedDestination() {
        var p = TspGenerator.generate(3,23,new GenerationOptions(10000,1000000)); var w = PreparedWorld.prepare(p);
        var g = new RefugeGraph(p,RefugeTopology.manual(3,List.of(new RefugeTopology.Edge(0,1),new RefugeTopology.Edge(1,2))));
        var r = new DijkstraShortestPathSolver().solve(g); var state = new WorldViewState();
        state.setModel(new ShortestPathSceneModel(w,g,0,2,Optional.of(r))); state.playback().finish();
        assertEquals(2,state.playback().edgeCount()); assertEquals(List.of(0,1,2),r.pathTo(2).orElseThrow());
        var limited = new DijkstraShortestPathSolver().solve(g,ShortestPathExecutionOptions.limits(0,0,1000),new ShortestPathCancellationToken());
        state.setModel(new ShortestPathSceneModel(w,g,0,2,Optional.of(limited)));
        assertEquals(0,state.playback().edgeCount()); assertTrue(state.playbackCursor().isEmpty());
        assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO,limited.vertexState(2));
    }
    @Test void zeroLengthGeometryKeepsExactPositionAndDoesNotInventAVisualDetour() {
        var point = new Vec3(7,8,9); var edge = new SegmentGeometry(point,point);
        assertEquals(point,edge.pointAt(.5)); assertEquals(point,edge.pointAt(1));
        assertThrows(IllegalArgumentException.class,()->edge.pointAt(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->edge.pointAt(1.1));
    }
}
