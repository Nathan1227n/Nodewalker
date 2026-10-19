package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.model.*;
import io.github.nathan1227n.nodewalker.core.shortestpath.*;
import io.github.nathan1227n.nodewalker.core.solver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.nathan1227n.nodewalker.app.operation.ExecutionRequest.Algorithm.*;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalSnapshot.Phase.*;

class OperationalControllerTest {
    static final class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>(); boolean stopped;
        public void execute(Runnable r) { if (stopped) throw new RejectedExecutionException(); tasks.add(r); }
        boolean step() { var r = tasks.poll(); if (r == null) return false; r.run(); return true; }
        public void shutdown() { stopped = true; }
        public List<Runnable> shutdownNow() { stopped = true; var pending = List.copyOf(tasks); tasks.clear(); return pending; }
        public boolean isShutdown() { return stopped; }
        public boolean isTerminated() { return stopped && tasks.isEmpty(); }
        public boolean awaitTermination(long t, TimeUnit u) { return isTerminated(); }
    }
    static final class Harness implements AutoCloseable {
        final ManualExecutor compute = new ManualExecutor(), visual = new ManualExecutor();
        final Queue<Runnable> ui = new ArrayDeque<>();
        final List<OperationalSnapshot> events = new ArrayList<>();
        final OperationalController controller;
        Harness() { this(new OperationalServices() { }); }
        Harness(OperationalServices services) { controller = new OperationalController(compute, visual, ui::add, services, events::add); }
        void ui() { while (!ui.isEmpty()) ui.remove().run(); }
        void drain() { for (int i = 0; i < 50; i++) { boolean worked = compute.step() | visual.step(); worked |= !ui.isEmpty(); ui(); if (!worked) return; } fail("non-quiescent controller"); }
        void generate(int n, long seed) { controller.generate(n, seed); drain(); assertEquals(INSTANCIA_PRONTA, controller.snapshot().phase(), controller.snapshot().diagnostic()); }
        OperationalSnapshot run(ExecutionRequest r) { controller.configure(r); drain(); controller.execute(); drain(); return controller.snapshot(); }
        public void close() { controller.close(); }
    }
    static ExecutionRequest request(ExecutionRequest.Algorithm algorithm) { return request(algorithm, 0, RefugeTopology.Kind.SPARSE_CONNECTED, 23, 0, 1_000_000, 1_000_000); }
    static ExecutionRequest request(ExecutionRequest.Algorithm algorithm, int origin, RefugeTopology.Kind kind, long seed, int extras, long nodes, long edges) {
        return new ExecutionRequest(algorithm, origin, kind, seed, extras, SolverExecutionOptions.limits(nodes, 5000), ShortestPathExecutionOptions.limits(10_000, edges, 5000));
    }
    @ParameterizedTest @ValueSource(ints = {3, 5, 8, 12})
    void reproducibleGenerationWithoutImplicitSearch(int n) {
        try (var h = new Harness()) {
            h.generate(n, 23); var p = h.controller.snapshot().problem(); long id = h.controller.snapshot().instanceId();
            assertNull(h.controller.snapshot().result()); assertTrue(h.controller.snapshot().history().isEmpty());
            h.generate(n, 23); var q = h.controller.snapshot().problem();
            assertNotSame(p, q); assertTrue(h.controller.snapshot().instanceId() > id); assertEquals(p.cities(), q.cities());
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) assertEquals(p.cost(i,j), q.cost(i,j));
        }
    }
    @Test void sameMatrixForBothTspSolversAndHistoryComparison() {
        try (var h = new Harness()) {
            h.generate(5, 23); var p = h.controller.snapshot().problem();
            var a = h.run(request(BRUTE_FORCE)); var b = h.run(request(BACKTRACKING));
            assertSame(p, b.problem()); assertSame(p, b.scene().problem()); assertTrue(b.runId() > a.runId());
            var ar = ((ExecutionRecord.Tsp)a.result()).result(); var br = ((ExecutionRecord.Tsp)b.result()).result();
            assertEquals(ar.cost(), br.cost()); assertEquals(ar.route().orElseThrow().indices(), br.route().orElseThrow().indices());
            assertEquals(CONCLUIDO, a.phase()); assertEquals(CONCLUIDO, b.phase());
            assertTrue(HudPresentation.history(b.history()).contains("mesmo custo ótimo e rota canônica"));
        }
    }
    @Test void destinationReusesDijkstraAndGeometryOriginAndTopologyInvalidate() {
        var calls = new AtomicInteger();
        try (var h = new Harness(new OperationalServices() {
            @Override public ShortestPathResult paths(RefugeGraph g, ExecutionRequest r, ShortestPathCancellationToken t) {
                calls.incrementAndGet(); return OperationalServices.super.paths(g,r,t);
            }
        })) {
            h.generate(5,23); var a = h.run(request(DIJKSTRA)); var world = a.scene().preparedWorld(); var r = a.result();
            h.controller.destination(4); h.drain(); var b = h.controller.snapshot();
            assertEquals(1, calls.get()); assertSame(r,b.result()); assertSame(world,b.scene().preparedWorld()); assertEquals(a.runId(),b.runId());
            h.controller.configure(request(DIJKSTRA, 2, RefugeTopology.Kind.SPARSE_CONNECTED,23,0,1000,1000)); h.drain();
            assertNull(h.controller.snapshot().result()); assertSame(a.problem(),h.controller.snapshot().problem());
            h.controller.execute(); h.drain(); assertEquals(2,calls.get());
            h.controller.configure(request(DIJKSTRA,2,RefugeTopology.Kind.COMPLETE,23,0,1000,1000)); h.drain();
            assertNull(h.controller.snapshot().result()); assertTrue(h.controller.snapshot().preflight().contains("E=10"));
        }
    }
    @Test void cancelBeforeSolverStillAcceptsCancelledResult() {
        try (var h = new Harness()) {
            h.generate(5,23); h.controller.execute(); long run = h.controller.snapshot().runId(); h.controller.cancel(); h.drain();
            assertEquals(CANCELADO,h.controller.snapshot().phase()); assertEquals(run,h.controller.snapshot().runId());
            assertEquals(SolverStatus.CANCELADO,((ExecutionRecord.Tsp)h.controller.snapshot().result()).result().status());
        }
    }
    @Test void cancelBeforeDijkstraStartsDoesNotCertifyTentativeDistances() {
        try (var h = new Harness()) {
            h.generate(5,23); h.controller.configure(request(DIJKSTRA)); h.drain(); h.controller.execute(); h.controller.cancel(); h.drain();
            var r = ((ExecutionRecord.Paths)h.controller.snapshot().result()).result(); assertEquals(CANCELADO,h.controller.snapshot().phase());
            for(int i=0;i<5;i++) { assertEquals(ShortestPathResult.VertexState.NAO_RESOLVIDO,r.vertexState(i)); assertTrue(r.distanceTo(i).isEmpty()); }
        }
    }
    @Test void exactDijkstraEdgeLimitPreservesConfirmedOrigin() {
        try (var h = new Harness()) {
            h.generate(5,23); var s = h.run(request(DIJKSTRA,0,RefugeTopology.Kind.COMPLETE,23,0,1000,0));
            var r = ((ExecutionRecord.Paths)s.result()).result();
            assertEquals(LIMITE_ATINGIDO,s.phase()); assertEquals(1,r.metrics().processedVertices()); assertEquals(0,r.metrics().examinedEdges());
            assertEquals(List.of(0),r.pathTo(0).orElseThrow()); assertTrue(r.distanceTo(1).isEmpty());
        }
    }
    @Test void limitedTspPreservesRealIncumbent() {
        try(var h=new Harness()) {
            h.generate(8,23); var s=h.run(request(BRUTE_FORCE,0,RefugeTopology.Kind.COMPLETE,23,0,1,1000));
            assertEquals(LIMITE_ATINGIDO,s.phase()); var r=((ExecutionRecord.Tsp)s.result()).result();
            assertTrue(r.route().isPresent()); assertFalse(r.optimalityGuaranteed()); assertFalse(s.scene().segments().isEmpty());
        }
    }
    @Test void staleAlreadyQueuedGenerationCannotReplaceNewInstance() {
        try(var h=new Harness()) {
            h.controller.generate(3,23); h.compute.step(); h.ui(); h.visual.step(); // publication queued for first world
            h.controller.generate(5,24); h.drain();
            assertEquals(5,h.controller.snapshot().problem().size()); assertEquals(24,h.controller.snapshot().seed());
            assertTrue(h.events.stream().filter(e->e.problem()!=null).allMatch(e->e.n()==5));
        }
    }
    @Test void staleQueuedSolverCannotReplaceNewExecutionOrAlgorithm() {
        try(var h=new Harness()) {
            h.generate(5,23); h.controller.execute(); h.compute.step(); // result enqueued, not consumed
            h.controller.configure(request(BRUTE_FORCE)); h.drain(); assertNull(h.controller.snapshot().result());
            h.controller.execute(); h.drain(); assertEquals(BRUTE_FORCE,h.controller.snapshot().result().request().algorithm());
            assertEquals(1,h.controller.snapshot().history().size());
        }
    }
    @Test void rerunGetsNewIdentitySharesWorldAndHistoryIsBounded() {
        try(var h=new Harness()) {
            h.generate(3,23); var world=h.controller.snapshot().scene().preparedWorld(); long run=0;
            for(int i=0;i<10;i++) { h.controller.execute(); h.drain(); assertTrue(h.controller.snapshot().runId()>run); run=h.controller.snapshot().runId(); assertSame(world,h.controller.snapshot().scene().preparedWorld()); }
            assertEquals(8,h.controller.snapshot().history().size());
        }
    }
    @Test void generationErrorIsNotSolverErrorAndQueuedOldErrorIsIgnored() {
        var attempts=new AtomicInteger();
        try(var h=new Harness(new OperationalServices() {
            @Override public TspProblem generate(int n,long seed,GenerationOptions o) { if(attempts.incrementAndGet()==1) throw new IllegalArgumentException("generation fixture"); return OperationalServices.super.generate(n,seed,o); }
        })) {
            h.controller.generate(3,23); h.compute.step(); h.controller.generate(5,23); h.drain(); assertEquals(INSTANCIA_PRONTA,h.controller.snapshot().phase());
        }
        try(var h=new Harness(new OperationalServices() { @Override public TspProblem generate(int n,long seed,GenerationOptions o) { throw new IllegalArgumentException("generation fixture"); } })) {
            h.controller.generate(3,23); h.drain(); assertEquals(ERRO,h.controller.snapshot().phase()); assertEquals("GERAÇÃO",h.controller.snapshot().stage()); assertNull(h.controller.snapshot().result());
        }
    }
    @Test void cancellationOfGenerationHasNoFabricatedSolverResult() {
        try(var h=new Harness()) { h.controller.generate(5,23); h.controller.cancel(); h.drain(); assertEquals(CANCELADO,h.controller.snapshot().phase()); assertNull(h.controller.snapshot().result()); assertEquals("GERAÇÃO",h.controller.snapshot().stage()); }
    }
    @Test void admissionRejectsBeforeGeneratingAndDoesNotDiscardActiveInstance() {
        try(var h=new Harness()) {
            h.generate(3,23); var old=h.controller.snapshot().problem();
            assertThrows(IllegalArgumentException.class,()->h.controller.generate(Integer.MAX_VALUE,23)); assertSame(old,h.controller.snapshot().problem());
            assertThrows(IllegalArgumentException.class,()->h.controller.configure(request(DIJKSTRA,3,RefugeTopology.Kind.COMPLETE,23,0,1000,1000)));
            assertThrows(IllegalArgumentException.class,()->h.controller.configure(request(DIJKSTRA,0,RefugeTopology.Kind.SPARSE_CONNECTED,23,2,1000,1000)));
        }
    }
    @Test void closeDropsPendingTasksAndQueuedUiResults() {
        var h=new Harness(); h.controller.generate(3,23); h.compute.step(); h.ui(); h.visual.step(); int events=h.events.size();
        h.close(); h.ui(); assertEquals(events,h.events.size()); assertTrue(h.compute.isShutdown()); assertTrue(h.visual.isShutdown());
        assertThrows(IllegalStateException.class,()->h.controller.generate(3,23));
    }
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void cancellationDuringRealWorkPreservesIncumbentOrConfirmedDistances(boolean dijkstra) throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var compute=Executors.newSingleThreadExecutor();
        var ui=new ConcurrentLinkedQueue<Runnable>(); var visual=new ManualExecutor();
        var service=new OperationalServices() {
            @Override public TspResult tsp(TspProblem p,ExecutionRequest r,SolverCancellationToken t) {
                return ControlledBruteForce.afterIncumbent(entered,release).solve(p,r.tspOptions(),t);
            }
            @Override public ShortestPathResult paths(RefugeGraph g,ExecutionRequest r,ShortestPathCancellationToken t) {
                return ControlledDijkstra.afterTwoVertices(entered,release).solve(g,r.origin(),r.pathOptions(),t);
            }
        };
        try(var c=new OperationalController(compute,visual,ui::add,service,e->{})) {
            c.generate(5,23); compute.submit(()->{}).get(5,TimeUnit.SECONDS); drainUi(ui); visual.step(); drainUi(ui); visual.step(); drainUi(ui);
            c.configure(request(dijkstra ? DIJKSTRA : BRUTE_FORCE));
            compute.submit(()->{}).get(5,TimeUnit.SECONDS); drainUi(ui); while(visual.step()) {} drainUi(ui);
            c.execute(); assertTrue(entered.await(5,TimeUnit.SECONDS)); c.cancel(); release.countDown(); compute.submit(()->{}).get(5,TimeUnit.SECONDS);
            drainUi(ui); while(visual.step()) {} drainUi(ui);
            assertEquals(CANCELADO,c.snapshot().phase());
            if(dijkstra) {
                var r=((ExecutionRecord.Paths)c.snapshot().result()).result(); assertEquals(2,r.metrics().processedVertices());
                assertEquals(2,java.util.stream.IntStream.range(0,5).filter(i->r.distanceTo(i).isPresent()).count());
                assertFalse(r.allDistancesResolved()); assertTrue(HudPresentation.of(c.snapshot()).status().contains("CANCELADO"));
            } else {
                var r=((ExecutionRecord.Tsp)c.snapshot().result()).result(); assertTrue(r.route().isPresent());
                assertFalse(r.optimalityGuaranteed()); assertFalse(c.snapshot().scene().segments().isEmpty());
            }
        } finally { release.countDown(); compute.shutdownNow(); }
    }
    @Test void zeroTimeLimitsAndZeroNodesArePassedThroughExactly() {
        try(var h=new Harness()) {
            h.generate(3,23);
            var zeroTsp=new ExecutionRequest(BRUTE_FORCE,0,RefugeTopology.Kind.COMPLETE,23,0,SolverExecutionOptions.limits(0,5000),ShortestPathExecutionOptions.demonstration());
            var a=h.run(zeroTsp); assertEquals(0,((ExecutionRecord.Tsp)a.result()).result().metrics().visitedNodes().orElseThrow()); assertEquals(LIMITE_ATINGIDO,a.phase());
            var zeroTime=new ExecutionRequest(DIJKSTRA,0,RefugeTopology.Kind.COMPLETE,23,0,SolverExecutionOptions.limits(1000,0),ShortestPathExecutionOptions.limits(1000,1000,0));
            var b=h.run(zeroTime); assertEquals(0,((ExecutionRecord.Paths)b.result()).result().metrics().processedVertices()); assertEquals(LIMITE_ATINGIDO,b.phase());
        }
    }
    @Test void solverFailureDoesNotMasqueradeAsGenerationFailure() {
        try(var h=new Harness(new OperationalServices(){ @Override public TspResult tsp(TspProblem p,ExecutionRequest r,SolverCancellationToken t){throw new IllegalStateException("solver fixture");} })) {
            h.generate(3,23); h.controller.execute(); h.drain(); assertEquals(ERRO,h.controller.snapshot().phase()); assertEquals("SOLVER / PREFLIGHT",h.controller.snapshot().stage()); assertNull(h.controller.snapshot().result());
        }
    }
    @Test void configurationIsNotReadyUntilItsPresentationIsPublished() {
        try(var h=new Harness()) {
            h.generate(3,23); h.controller.configure(request(DIJKSTRA)); h.compute.step(); h.ui();
            assertEquals(VALIDANDO_EXECUCAO,h.controller.snapshot().phase());
            h.visual.step(); assertEquals(VALIDANDO_EXECUCAO,h.controller.snapshot().phase()); h.ui();
            assertEquals(INSTANCIA_PRONTA,h.controller.snapshot().phase());
            assertInstanceOf(io.github.nathan1227n.nodewalker.app.world.ShortestPathSceneModel.class,h.controller.snapshot().scene());
        }
    }
    @Test void shortestPathMemoryPreflightRejectsBeforeInvokingSolver() {
        var calls=new AtomicInteger();
        try(var h=new Harness(new OperationalServices(){@Override public ShortestPathResult paths(RefugeGraph g,ExecutionRequest r,ShortestPathCancellationToken t){calls.incrementAndGet();return OperationalServices.super.paths(g,r,t);}})) {
            h.generate(3,23);
            h.controller.configure(new ExecutionRequest(DIJKSTRA,0,RefugeTopology.Kind.COMPLETE,23,0,SolverExecutionOptions.limits(1000,2000),ShortestPathExecutionOptions.demonstration().withMemoryBudget(1)));
            h.drain(); assertEquals(ERRO,h.controller.snapshot().phase()); assertEquals(0,calls.get()); assertNull(h.controller.snapshot().result());
            assertTrue(h.controller.snapshot().diagnostic().contains("preflight refused"));
        }
    }
    @Test void obsoleteDestinationPublicationCannotReplaceLatestDestination() {
        try(var h=new Harness()) {
            h.generate(5,23); var initial=h.run(request(DIJKSTRA));
            h.controller.destination(2); h.visual.step(); // queued for UI
            h.controller.destination(4); h.drain();
            var displayed=(io.github.nathan1227n.nodewalker.app.world.ShortestPathSceneModel)h.controller.snapshot().scene();
            assertEquals(4,displayed.destination()); assertSame(initial.result(),h.controller.snapshot().result()); assertEquals(initial.runId(),h.controller.snapshot().runId());
        }
    }
    @Test void tspOriginIsAlwaysZeroAfterSwitchingFromDijkstra() {
        var configured=request(BRUTE_FORCE,2,RefugeTopology.Kind.COMPLETE,23,0,1000,1000); assertEquals(0,configured.origin());
    }
    @ParameterizedTest @ValueSource(longs={1,2,3})
    void exactTspBudgetBoundaryIsPreserved(long budget) {
        try(var h=new Harness()) {
            h.generate(3,23); var s=h.run(request(BRUTE_FORCE,0,RefugeTopology.Kind.COMPLETE,23,0,budget,1000));
            assertEquals(Math.min(2,budget),((ExecutionRecord.Tsp)s.result()).result().metrics().visitedNodes().orElseThrow());
            assertEquals(budget<2 ? LIMITE_ATINGIDO : CONCLUIDO,s.phase());
        }
    }
    @ParameterizedTest @ValueSource(longs={2,3,4})
    void exactDijkstraVertexBoundaryIsPreserved(long budget) {
        try(var h=new Harness()) {
            h.generate(3,23);
            var config=new ExecutionRequest(DIJKSTRA,0,RefugeTopology.Kind.COMPLETE,23,0,SolverExecutionOptions.limits(1000,2000),ShortestPathExecutionOptions.limits(budget,1000,2000));
            var s=h.run(config); assertEquals(Math.min(3,budget),((ExecutionRecord.Paths)s.result()).result().metrics().processedVertices());
            assertEquals(budget<3 ? LIMITE_ATINGIDO : CONCLUIDO,s.phase());
        }
    }
    @Test void inconsistentSolverResultIsRejectedBeforePublishingOrAddingHistory() {
        try(var h=new Harness(new OperationalServices(){
            @Override public TspResult tsp(TspProblem p,ExecutionRequest r,SolverCancellationToken t){return new BruteForceSolver().solve(new TspProblem(p.cities(),new long[p.size()][p.size()]));}
        })) {
            h.generate(3,23); h.controller.execute(); h.drain();
            assertEquals(ERRO,h.controller.snapshot().phase()); assertNull(h.controller.snapshot().result()); assertTrue(h.controller.snapshot().history().isEmpty());
            assertTrue(h.events.stream().noneMatch(e->e.result()!=null));
        }
    }
    static void drainUi(Queue<Runnable> ui) { while(!ui.isEmpty()) ui.remove().run(); }
}
