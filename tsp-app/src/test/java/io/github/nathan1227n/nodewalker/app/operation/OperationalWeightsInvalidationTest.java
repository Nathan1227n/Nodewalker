package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.app.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalControllerTest.*;
import static io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest.Algorithm.*;
import static org.junit.jupiter.api.Assertions.*;

/** Manual queues hold both completed presentations and UI publications without sleeps. */
class OperationalWeightsInvalidationTest {
    private static WorldViewState displayed(Harness h) {
        var state=new WorldViewState();state.setModel(h.controller.snapshot().scene());state.setWeightMode(EdgeWeightMode.CONNECTIONS);
        assertFalse(state.weightSelection().candidates().isEmpty());
        assertTrue(CostBreakdown.ofScene(state.model()).orElseThrow().total().isPresent());return state;
    }
    private static void pending(Harness h,WorldViewState state) {
        state.setModel(h.controller.snapshot().scene());
        assertInstanceOf(PendingSceneModel.class,state.model());
        assertTrue(state.weightSelection().candidates().isEmpty());assertTrue(CostBreakdown.ofScene(state.model()).isEmpty());
    }
    @ParameterizedTest @EnumSource(ExecutionRequest.Algorithm.class)
    void switchingAlgorithmInvalidatesEvenAnAlreadyQueuedOldPresentation(ExecutionRequest.Algorithm target) {
        try(var h=new Harness()) {
            h.generate(5,23);h.run(request(target==DIJKSTRA?BACKTRACKING:DIJKSTRA));
            var state=displayed(h);var world=state.model().preparedWorld();state.select(3);var camera=state.camera();
            h.controller.configure(request(target));pending(h,state);
            h.compute.step();h.ui();h.visual.step(); // presentation waiting in UI queue
            h.controller.configure(request(target==BACKTRACKING?BRUTE_FORCE:BACKTRACKING));pending(h,state);
            h.ui();pending(h,state);h.drain();state.setModel(h.controller.snapshot().scene());
            assertSame(world,state.model().preparedWorld());assertSame(camera,state.camera());assertEquals(3,state.selected());
            assertTrue(CostBreakdown.ofScene(state.model()).orElseThrow().total().isEmpty());
            assertTrue(state.weightSelection().candidates().stream().noneMatch(EdgeWeightPolicy.WeightedEdge::solution));
        }
    }
    @Test void rerunHasNoOldLabelsOrSumsBeforeAnyWorkerRuns() {
        try(var h=new Harness()) {
            h.generate(5,23);var before=h.run(request(BACKTRACKING));var state=displayed(h);
            h.controller.execute();pending(h,state);assertTrue(h.controller.snapshot().runId()>before.runId());
            assertSame(before.scene().preparedWorld(),state.model().preparedWorld());
            h.drain();state.setModel(h.controller.snapshot().scene());
            assertEquals(((ExecutionRecord.Tsp)h.controller.snapshot().result()).result().cost().orElseThrow(),CostBreakdown.ofScene(state.model()).orElseThrow().total().orElseThrow().longValueExact());
        }
    }
    @Test void destinationPublicationCannotRestoreOldTermsAndDoesNotRunSolver() {
        try(var h=new Harness()) {
            h.generate(5,23);var before=h.run(request(DIJKSTRA));var state=displayed(h);
            h.controller.destination(2);pending(h,state);h.visual.step();
            h.controller.destination(4);h.ui();pending(h,state);h.drain();state.setModel(h.controller.snapshot().scene());
            assertSame(before.result(),h.controller.snapshot().result());assertEquals(before.runId(),h.controller.snapshot().runId());
            var result=((ExecutionRecord.Paths)before.result()).result();var breakdown=CostBreakdown.ofScene(state.model()).orElseThrow();
            assertEquals(4,breakdown.destination());assertEquals(result.distanceTo(4),breakdown.total());
        }
    }
    @Test void newInstanceDiscardsQueuedTermsAndTheirOriginalMatrix() {
        try(var h=new Harness()) {
            h.generate(5,23);h.run(request(DIJKSTRA));var state=displayed(h);
            h.controller.destination(2);h.visual.step();h.controller.generate(3,24);h.ui();
            state.clear();assertTrue(state.weightSelection().candidates().isEmpty());
            h.drain();state.setModel(h.controller.snapshot().scene());assertEquals(3,state.model().problem().size());
            assertTrue(CostBreakdown.ofScene(state.model()).orElseThrow().total().isEmpty());
            assertTrue(state.weightSelection().candidates().stream().noneMatch(EdgeWeightPolicy.WeightedEdge::solution));
        }
    }
}
