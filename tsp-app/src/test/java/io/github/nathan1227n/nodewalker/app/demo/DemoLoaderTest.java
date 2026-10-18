package io.github.nathan1227n.nodewalker.app.demo;

import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import io.github.nathan1227n.nodewalker.core.generator.GenerationCancellationToken;
import io.github.nathan1227n.nodewalker.core.generator.GenerationOptions;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import io.github.nathan1227n.nodewalker.core.solver.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class DemoLoaderTest {
    private final ManualExecutor worker = new ManualExecutor();
    private final ArrayDeque<Runnable> ui = new ArrayDeque<>();
    private final List<TspSceneModel> deliveries = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    @Test void oldCompletionAlreadyQueuedCannotReplaceNewRequest() {
        var model = emptyModel();
        var loader = new DemoLoader(worker, ui::add, (d, g, s) -> model);
        loader.load(DemoCatalog.TRIANGLE, deliveries::add, errors::add);
        worker.next();
        assertEquals(1, ui.size());
        loader.load(DemoCatalog.OUTPOSTS, deliveries::add, errors::add);
        ui.remove().run();
        assertTrue(deliveries.isEmpty());
        worker.next(); ui.remove().run();
        assertEquals(List.of(model), deliveries);
        loader.close();
    }

    @Test void cancelAndCloseInvalidateQueuedDeliveryAndUseSeparateTokens() {
        var generation = new ArrayList<GenerationCancellationToken>();
        var search = new ArrayList<SolverCancellationToken>();
        var model = emptyModel();
        var loader = new DemoLoader(worker, ui::add, (d, g, s) -> {
            generation.add(g); search.add(s); return model;
        });
        loader.load(DemoCatalog.TRIANGLE, deliveries::add, errors::add); worker.next();
        loader.cancel(); ui.remove().run();
        assertTrue(generation.getFirst().isCancellationRequested());
        assertTrue(search.getFirst().isCancellationRequested());
        assertTrue(deliveries.isEmpty());
        loader.load(DemoCatalog.VALLEY, deliveries::add, errors::add); worker.next();
        assertNotSame(generation.get(0), generation.get(1));
        assertNotSame(search.get(0), search.get(1));
        assertFalse(generation.get(1).isCancellationRequested());
        loader.close(); ui.remove().run(); loader.close();
        assertTrue(generation.get(1).isCancellationRequested());
        assertTrue(search.get(1).isCancellationRequested());
        assertTrue(worker.isShutdown());
        assertTrue(deliveries.isEmpty());
        assertThrows(IllegalStateException.class, () -> loader.load(DemoCatalog.TRIANGLE, deliveries::add, errors::add));
    }

    @Test void replacedWorkNeverStartsAndRealFailureIsNotSolverResult() {
        int[] calls = {0};
        var loader = new DemoLoader(worker, ui::add, (d, g, s) -> {
            calls[0]++; throw new IllegalArgumentException("controlled generation failure");
        });
        loader.load(DemoCatalog.FRONTIER, deliveries::add, errors::add);
        loader.load(DemoCatalog.TRIANGLE, deliveries::add, errors::add);
        worker.next(); worker.next(); ui.remove().run();
        assertEquals(1, calls[0]);
        assertTrue(deliveries.isEmpty());
        assertEquals(List.of("IllegalArgumentException: controlled generation failure"), errors);
        loader.close();
    }

    @Test void obsoleteErrorIsAlsoSuppressed() {
        var loader = new DemoLoader(worker, ui::add, (d, g, s) -> { throw new IllegalStateException("old error"); });
        loader.load(DemoCatalog.TRIANGLE, deliveries::add, errors::add); worker.next();
        loader.cancel(); ui.remove().run();
        assertTrue(errors.isEmpty()); loader.close();
    }

    @Test @org.junit.jupiter.api.Timeout(10)
    void cancellationDuringPreparationPreservesThreadInterruptAndSuppressesDelivery() throws Exception {
        var entered = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var release = new Semaphore(0);
        var interrupted = new AtomicBoolean();
        var bothTokensCancelled = new AtomicBoolean();
        var prepared = emptyModel();
        // This test synchronizes on phases, not elapsed time. Timeout is only a deadlock failure guard.
        try (var loader = new DemoLoader(Executors.newSingleThreadExecutor(), Runnable::run, (d, g, s) -> {
            entered.countDown(); release.acquireUninterruptibly();
            interrupted.set(Thread.currentThread().isInterrupted());
            bothTokensCancelled.set(g.isCancellationRequested() && s.isCancellationRequested());
            exited.countDown(); return prepared;
        })) {
            loader.load(DemoCatalog.TRIANGLE, ignored -> fail("Stale model delivered"), message -> fail(message));
            entered.await(); loader.cancel(); release.release(); exited.await();
            assertTrue(interrupted.get()); assertTrue(bothTokensCancelled.get());
        } finally { release.release(); }
    }

    @ParameterizedTest @EnumSource(DemoCatalog.class)
    void fixedCatalogAlwaysUsesFiniteSeparatedBudgets(DemoCatalog demo) {
        assertTrue(demo.preflight().executionAllowed());
        assertTrue(demo.options().maxVisitedNodes().isPresent());
        assertTrue(demo.options().maxElapsedMillis().isPresent());
        assertFalse(demo.options().allowUnlimited());
        var p = TspGenerator.generate(demo.cityCount(), demo.seed(), new GenerationOptions(
                DemoCatalog.GENERATION_CANDIDATES, DemoCatalog.GENERATION_BYTES));
        // Node-only equivalent keeps assertions independent of machine speed; production also caps time.
        var result = new BacktrackingSolver().solve(p, SolverExecutionOptions.nodeLimit(demo.nodeBudget()), new SolverCancellationToken());
        var model = TspSceneModel.of(p, Optional.of(result));
        assertEquals(demo.cityCount(), model.refuges().size());
        assertEquals(demo.cityCount(), model.segments().size());
        assertEquals(result.optimalityGuaranteed(), model.certified());
        assertTrue(result.metrics().visitedNodes().orElseThrow() <= demo.nodeBudget());
        if (demo == DemoCatalog.FRONTIER) {
            assertEquals(SolverStatus.LIMITE_ATINGIDO, result.status());
            assertFalse(model.certified());
        }
    }

    private TspSceneModel emptyModel() {
        return TspSceneModel.of(TspGenerator.generate(3, 23, new GenerationOptions(1000, 1_000_000)), Optional.empty());
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean stopped;
        void next() { tasks.remove().run(); }
        @Override public void execute(Runnable action) { tasks.add(action); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; var left = List.copyOf(tasks); tasks.clear(); return left; }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return stopped; }
    }
}
