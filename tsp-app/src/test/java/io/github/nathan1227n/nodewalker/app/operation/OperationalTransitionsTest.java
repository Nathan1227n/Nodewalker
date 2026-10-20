package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.RefugeGraph;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest.Algorithm.*;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalSnapshot.Phase.*;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalControllerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class OperationalTransitionsTest {
    private static void pending(OperationalSnapshot snapshot, PreparedWorld world) {
        assertInstanceOf(PendingSceneModel.class,snapshot.scene());
        assertSame(world,snapshot.scene().preparedWorld());
        assertTrue(snapshot.scene().segments().isEmpty());
        assertTrue(snapshot.scene().secondaryConnections(0).isEmpty());
    }
    @ParameterizedTest @CsvSource({"BACKTRACKING,DIJKSTRA","DIJKSTRA,BRUTE_FORCE","BACKTRACKING,BACKTRACKING"})
    void switchingProblemOrRerunningImmediatelyRemovesOldResponse(ExecutionRequest.Algorithm before,ExecutionRequest.Algorithm after) {
        try(var h=new Harness()) {
            h.generate(5,23); var old=h.run(request(before)); var world=old.scene().preparedWorld();
            assertFalse(old.scene().segments().isEmpty()); int event=h.events.size();
            if(before==after) h.controller.execute(); else h.controller.configure(request(after));
            assertNull(h.controller.snapshot().result()); pending(h.controller.snapshot(),world);
            assertEquals(VALIDANDO_EXECUCAO,h.controller.snapshot().phase());
            assertTrue(h.events.subList(event,h.events.size()).stream().allMatch(s->s.scene().segments().isEmpty()));
            // Leave presentation pending while consuming the other worker/UI deliveries.
            if(before!=after) { h.compute.step(); h.ui(); pending(h.controller.snapshot(),world); }
            h.drain(); assertSame(world,h.controller.snapshot().scene().preparedWorld());
        }
    }
    @Test void configurationInvalidatesEvenACompletedSceneAlreadyQueuedForUi() {
        try(var h=new Harness()) {
            h.generate(5,23); var old=h.run(request(BACKTRACKING)); var world=old.scene().preparedWorld();
            h.controller.execute(); h.compute.step(); h.ui();
            while(h.visual.step()) {} // both blank and completed rerun scenes are queued for UI
            h.controller.configure(request(DIJKSTRA)); pending(h.controller.snapshot(),world);
            int event=h.events.size(); h.ui(); pending(h.controller.snapshot(),world);
            h.drain(); assertNull(h.controller.snapshot().result());
            assertInstanceOf(ShortestPathSceneModel.class,h.controller.snapshot().scene());
            assertTrue(h.events.subList(event,h.events.size()).stream().allMatch(s->s.result()==null && s.scene().segments().isEmpty()));
        }
    }
    @Test void newConfigurationCannotBeOverwrittenByOldConfigurationPresentation() {
        try(var h=new Harness()) {
            h.generate(5,23); var old=h.run(request(BACKTRACKING)); var world=old.scene().preparedWorld();
            h.controller.configure(request(DIJKSTRA)); h.compute.step(); h.ui(); h.visual.step();
            h.controller.configure(request(BRUTE_FORCE)); pending(h.controller.snapshot(),world);
            h.ui(); pending(h.controller.snapshot(),world); h.drain();
            assertInstanceOf(TspSceneModel.class,h.controller.snapshot().scene());
            assertEquals(BRUTE_FORCE,h.controller.snapshot().request().algorithm()); assertTrue(h.controller.snapshot().scene().segments().isEmpty());
        }
    }
    @Test void destinationChangeHidesOldPathWhileRevisionIsPendingAndRetainsTheSearch() {
        try(var h=new Harness()) {
            h.generate(5,23); var old=h.run(request(DIJKSTRA));
            h.controller.destination(2); pending(h.controller.snapshot(),old.scene().preparedWorld());
            assertSame(old.result(),h.controller.snapshot().result()); assertEquals(old.runId(),h.controller.snapshot().runId());
            h.drain(); assertEquals(2,((ShortestPathSceneModel)h.controller.snapshot().scene()).destination());
        }
    }
    enum Cut { GENERATION_WAITING, GENERATION_UI, WORLD_WAITING, WORLD_UI, TOPOLOGY_WAITING, TOPOLOGY_UI, SCENE_WAITING, SCENE_UI }
    @ParameterizedTest @EnumSource(Cut.class)
    void cancellingPreparationInvalidatesQueuedWorkAndUiWithoutInventingSolverResponse(Cut cut) {
        try(var h=new Harness()) {
            if(cut.ordinal()<=Cut.WORLD_UI.ordinal()) {
                h.controller.generate(5,23);
                if(cut!=Cut.GENERATION_WAITING) h.compute.step();
                if(cut==Cut.WORLD_WAITING || cut==Cut.WORLD_UI) h.ui();
                if(cut==Cut.WORLD_UI) h.visual.step();
            } else {
                h.generate(5,23); h.controller.configure(request(DIJKSTRA));
                if(cut!=Cut.TOPOLOGY_WAITING) h.compute.step();
                if(cut==Cut.SCENE_WAITING || cut==Cut.SCENE_UI) h.ui();
                if(cut==Cut.SCENE_UI) h.visual.step();
            }
            int event=h.events.size(); h.controller.cancel();
            assertEquals(CANCELADO,h.controller.snapshot().phase()); assertNull(h.controller.snapshot().result());
            assertTrue(h.controller.snapshot().diagnostic().contains("nenhuma busca iniciada"));
            assertEquals(CANCELAMENTO_SOLICITADO,h.events.get(event).phase()); assertEquals(CANCELADO,h.events.get(event+1).phase());
            h.drain(); assertEquals(CANCELADO,h.controller.snapshot().phase()); assertTrue(h.controller.snapshot().history().isEmpty());
            assertTrue(h.events.subList(event,h.events.size()).stream().noneMatch(s->s.phase()==INSTANCIA_PRONTA || s.result()!=null));
            if(h.controller.snapshot().problem()!=null) {
                var world=h.controller.snapshot().scene().preparedWorld(); h.controller.execute(); h.drain();
                assertEquals(CONCLUIDO,h.controller.snapshot().phase()); assertSame(world,h.controller.snapshot().scene().preparedWorld());
            }
        }
    }
    @Test void cancellingTspConfigurationPresentationPreservesWorldAndCanRetrySameConfiguration() {
        try(var h=new Harness()) {
            h.generate(5,23); var old=h.run(request(DIJKSTRA));
            h.controller.configure(request(BRUTE_FORCE)); h.visual.step(); h.controller.cancel(); h.ui();
            assertEquals(CANCELADO,h.controller.snapshot().phase()); pending(h.controller.snapshot(),old.scene().preparedWorld());
            h.controller.configure(request(BRUTE_FORCE)); h.drain(); assertEquals(INSTANCIA_PRONTA,h.controller.snapshot().phase());
            assertNull(h.controller.snapshot().result()); h.controller.execute(); h.drain(); assertEquals(CONCLUIDO,h.controller.snapshot().phase());
        }
    }
    static Stream<Arguments> completedDeliveries() {
        return Arrays.stream(ExecutionRequest.Algorithm.values()).flatMap(a->Stream.of(Arguments.of(a,false),Arguments.of(a,true)));
    }
    @ParameterizedTest @MethodSource("completedDeliveries")
    void lateCancelPreservesCompletedSolverBeforeOrAfterItsUiDelivery(ExecutionRequest.Algorithm algorithm,boolean accepted) {
        try(var h=new Harness()) {
            h.generate(5,23); h.controller.configure(request(algorithm)); h.drain(); h.controller.execute(); h.compute.step();
            if(accepted) { h.ui(); while(h.visual.step()) {} } // completed presentation can also be queued
            long run=h.controller.snapshot().runId(); h.controller.cancel(); h.drain();
            assertEquals(CONCLUIDO,h.controller.snapshot().phase()); assertEquals("CONCLUIDO",h.controller.snapshot().result().status());
            assertEquals(run,h.controller.snapshot().runId()); assertEquals(1,h.controller.snapshot().history().size());
            assertFalse(h.controller.snapshot().scene().segments().isEmpty());
            var result=h.controller.snapshot().result(); h.controller.cancel(); assertSame(result,h.controller.snapshot().result());
        }
    }
    enum Running { GENERATION, TOPOLOGY, WORLD }
    @ParameterizedTest @EnumSource(Running.class)
    void cancellingRunningPreparationCrossesBarrierAndRejectsItsLaterPublication(Running running) throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var compute=Executors.newSingleThreadExecutor(); var visual=Executors.newSingleThreadExecutor();
        var ui=new ConcurrentLinkedQueue<Runnable>(); var events=new ArrayList<OperationalSnapshot>();
        var services=new OperationalServices() {
            private void barrier(Running stage) {
                if(stage!=running) return;
                entered.countDown();
                try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("preparation barrier timed out"); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new CancellationException(); }
            }
            @Override public TspProblem generate(int n,long seed,GenerationOptions options) { barrier(Running.GENERATION); return OperationalServices.super.generate(n,seed,options); }
            @Override public RefugeGraph prepareGraph(TspProblem p,ExecutionRequest request,GenerationCancellationToken token) { barrier(Running.TOPOLOGY); return OperationalServices.super.prepareGraph(p,request,token); }
            @Override public PreparedWorld prepareWorld(TspProblem p,Runnable checkpoint) {
                return PreparedWorld.prepare(p,()->{barrier(Running.WORLD); checkpoint.run();});
            }
        };
        try(var controller=new OperationalController(compute,visual,ui::add,services,events::add)) {
            controller.generate(5,23);
            if(running!=Running.GENERATION) { compute.submit(()->{}).get(5,TimeUnit.SECONDS); drainUi(ui); }
            if(running==Running.TOPOLOGY) {
                visual.submit(()->{}).get(5,TimeUnit.SECONDS); drainUi(ui);
                visual.submit(()->{}).get(5,TimeUnit.SECONDS); drainUi(ui);
                assertEquals(INSTANCIA_PRONTA,controller.snapshot().phase()); controller.configure(request(DIJKSTRA));
            }
            assertTrue(entered.await(5,TimeUnit.SECONDS)); controller.cancel(); int cancelled=events.size();
            assertEquals(CANCELADO,controller.snapshot().phase()); release.countDown();
            compute.submit(()->{}).get(5,TimeUnit.SECONDS); visual.submit(()->{}).get(5,TimeUnit.SECONDS); drainUi(ui);
            assertEquals(CANCELADO,controller.snapshot().phase()); assertNull(controller.snapshot().result());
            assertEquals(cancelled,events.size()); assertTrue(controller.snapshot().history().isEmpty());
        } finally { release.countDown(); compute.shutdownNow(); visual.shutdownNow(); }
    }
    @Test void pendingPresentationPreservesNavigationGeometryAndSettingsWithoutACertifiedPath() {
        try(var h=new Harness()) {
            h.generate(5,23); var old=h.run(request(BACKTRACKING));
            var state=new WorldViewState(); state.setModel(old.scene()); state.select(3); state.camera().orbit(19,8); state.camera().zoom(25);
            state.setDecoration(false); state.setConnections(true); state.playback().finish();
            var camera=state.camera(); var pose=camera.pose(900,700); var terrain=old.scene().terrain(false);
            h.controller.execute(); state.setModel(h.controller.snapshot().scene());
            assertSame(camera,state.camera()); assertEquals(pose,state.camera().pose(900,700)); assertEquals(3,state.selected());
            assertSame(terrain,state.model().terrain(false)); assertFalse(state.decoration()); assertTrue(state.connections());
            assertEquals(0,state.playback().edgeCount()); assertTrue(state.playbackCursor().isEmpty()); assertFalse(state.model().solidPath());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lateCancelPreservesAnAlreadyLimitedSolverAndItsValidPartialData(boolean dijkstra) {
        try(var h=new Harness()) {
            h.generate(5,23);
            h.controller.configure(request(dijkstra?DIJKSTRA:BRUTE_FORCE,0,
                    io.github.nathan1227n.nodewalker.core.graph.RefugeTopology.Kind.COMPLETE,23,0,1,0));
            h.drain();h.controller.execute();h.compute.step();h.controller.cancel();h.drain();
            assertEquals(LIMITE_ATINGIDO,h.controller.snapshot().phase());
            if(h.controller.snapshot().result() instanceof ExecutionRecord.Tsp tsp) {
                assertTrue(tsp.result().route().isPresent());assertFalse(tsp.result().optimalityGuaranteed());
            } else {
                var paths=((ExecutionRecord.Paths)h.controller.snapshot().result()).result();
                assertEquals(List.of(0),paths.pathTo(0).orElseThrow());assertTrue(paths.distanceTo(1).isEmpty());
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void inconsistentPreparedInstanceIsRejectedBeforePublication(boolean topology) {
        try(var h=new Harness(new OperationalServices(){
            @Override public PreparedWorld prepareWorld(TspProblem problem,Runnable checkpoint) {
                return OperationalServices.super.prepareWorld(topology?problem:TspGenerator.generate(3,24),checkpoint);
            }
            @Override public RefugeGraph prepareGraph(TspProblem problem,ExecutionRequest request,GenerationCancellationToken token) {
                return OperationalServices.super.prepareGraph(TspGenerator.generate(3,24),request,token);
            }
        })) {
            if(topology) {h.generate(3,23);h.controller.configure(request(DIJKSTRA));h.drain();}
            else {h.controller.generate(3,23);h.drain();}
            assertEquals(ERRO,h.controller.snapshot().phase());assertNull(h.controller.snapshot().result());
            assertTrue(h.controller.snapshot().diagnostic().contains("different instance"));
            if(topology)assertInstanceOf(PendingSceneModel.class,h.controller.snapshot().scene());else assertNull(h.controller.snapshot().scene());
        }
    }

    @Test void cancellingRetryInvalidatesItsAlreadyQueuedReadyCallbackAndAutomaticSearch() {
        try(var h=new Harness()) {
            h.generate(5,23);h.controller.configure(request(DIJKSTRA));h.controller.cancel();h.drain();
            h.controller.execute();h.compute.step();h.ui();h.visual.step(); // retry ready callback is queued
            h.controller.cancel();h.drain();
            assertEquals(CANCELADO,h.controller.snapshot().phase());assertEquals(0,h.controller.snapshot().runId());
            assertNull(h.controller.snapshot().result());assertTrue(h.controller.snapshot().history().isEmpty());
            h.controller.configure(request(BRUTE_FORCE));h.drain();
            assertEquals(INSTANCIA_PRONTA,h.controller.snapshot().phase());assertEquals(0,h.controller.snapshot().runId());
        }
    }

}
