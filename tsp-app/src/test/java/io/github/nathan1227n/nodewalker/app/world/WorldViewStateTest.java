package io.github.nathan1227n.nodewalker.app.world;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldViewStateTest {
    @Test void rendererChangesPreserveSelectionCameraAndOptions() {
        var model = TspSceneModel.of(WorldContractsTest.zeroProblem(), Optional.empty());
        var state = new WorldViewState(); state.setModel(model); state.select(2);
        state.setDecoration(false); state.setSimplified(true); state.setConnections(true);
        state.camera().orbit(123, 42); state.camera().zoom(55);
        var camera = state.camera(); var pose = camera.pose(900, 600);
        // Both renderers receive this same state, without copying/changing logical data.
        state.setModel(model.withResult(Optional.empty()));
        assertSame(camera, state.camera());
        assertEquals(pose, state.camera().pose(900, 600));
        assertEquals(2, state.selected()); assertTrue(state.simplified()); assertFalse(state.decoration()); assertTrue(state.connections());
        assertSame(model.problem(), state.model().problem());
        assertThrows(IllegalArgumentException.class, () -> state.select(3));
        state.clear(); assertNull(state.model());
        state.setModel(TspSceneModel.of(WorldContractsTest.zeroProblem(), Optional.empty()));
        assertEquals(0, state.selected()); assertNotSame(camera, state.camera());
        assertTrue(state.simplified());
    }

    @Test void zoomOrbitFocusAndTopRemainFiniteAtBoundaries() {
        var camera = new OrbitCamera(java.util.List.of(Vec3.ZERO), 760);
        for (int i = 0; i < 20; i++) { camera.orbit(1e9, 1e9); camera.zoom(1e9); }
        var pose = camera.pose(1, 1);
        assertTrue(pose.distance() > 0); assertTrue(pose.farClip() > pose.nearClip());
        assertEquals(0, pose.right().dot(pose.down()), 1e-12);
        camera.top(); pose = camera.pose(800, 600);
        assertEquals(1, pose.forward().y(), 1e-12);
        camera.focus(new Vec3(500, -500, -500));
        assertTrue(camera.pose(800, 600).farClip() > 2000);
        camera.reset(); assertEquals(new OrbitCamera(java.util.List.of(Vec3.ZERO), 760).pose(800, 600), camera.pose(800, 600));
    }
}
