package io.github.nathan1227n.nodewalker.app.demo;

import io.github.nathan1227n.nodewalker.app.world.TspSceneModel;
import io.github.nathan1227n.nodewalker.core.generator.GenerationCancellationToken;
import io.github.nathan1227n.nodewalker.core.solver.SolverCancellationToken;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Minimal latest-request-wins demo loader. No FX dependency: the caller supplies the UI dispatcher.
 * Both the worker completion and the already queued UI delivery must still own the current id.
 * Closing/cancelling never waits for a worker. Generation and search have distinct tokens.
 */
public final class DemoLoader implements AutoCloseable {
    private final ExecutorService executor;
    private final Consumer<Runnable> dispatch;
    private final Preparation preparation;
    private long version;
    private boolean closed;
    private Future<?> pending;
    private GenerationCancellationToken generationToken;
    private SolverCancellationToken solverToken;

    public DemoLoader(ExecutorService executor, Consumer<Runnable> dispatch) {
        this(executor, dispatch, DemoCatalog::prepare);
    }

    public DemoLoader(ExecutorService executor, Consumer<Runnable> dispatch, Preparation preparation) {
        this.executor = Objects.requireNonNull(executor);
        this.dispatch = Objects.requireNonNull(dispatch);
        this.preparation = Objects.requireNonNull(preparation);
    }

    public synchronized void load(DemoCatalog demo, Consumer<TspSceneModel> success, Consumer<String> failure) {
        if (closed) throw new IllegalStateException("Loader is closed");
        Objects.requireNonNull(demo); Objects.requireNonNull(success); Objects.requireNonNull(failure);
        cancel();
        long request = version;
        var generation = new GenerationCancellationToken();
        var solver = new SolverCancellationToken();
        generationToken = generation;
        solverToken = solver;
        pending = executor.submit(() -> {
            TspSceneModel model;
            try {
                model = Objects.requireNonNull(preparation.prepare(demo, generation, solver));
            } catch (RuntimeException ex) {
                // A generation failure is not a TspResult. Preserve real solver states in successful models.
                String diagnostic = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                deliver(request, () -> failure.accept(diagnostic));
                return;
            }
            // Delivery/UI failures must not be relabelled as preparation failures.
            deliver(request, () -> success.accept(model));
        });
    }

    private void deliver(long request, Runnable action) {
        synchronized (this) { if (closed || request != version) return; }
        dispatch.accept(() -> {
            synchronized (this) {
                if (!closed && request == version) action.run();
            }
        });
    }

    public synchronized void cancel() {
        version++;
        if (generationToken != null) generationToken.cancel();
        if (solverToken != null) solverToken.cancel();
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    @Override public synchronized void close() {
        if (closed) return;
        cancel();
        closed = true;
        executor.shutdownNow();
    }

    @FunctionalInterface public interface Preparation {
        TspSceneModel prepare(DemoCatalog demo, GenerationCancellationToken generation, SolverCancellationToken solver);
    }
}
