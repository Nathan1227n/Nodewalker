package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.app.demo.DemoCatalog;
import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.core.generator.*;
import io.github.nathan1227n.nodewalker.core.graph.*;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathCancellationToken;
import io.github.nathan1227n.nodewalker.core.solver.SolverCancellationToken;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static io.github.nathan1227n.nodewalker.app.operation.OperationalSnapshot.Phase.*;

/**
 * No FX dependency. Commands and publications are serialized by the owner/dispatcher.
 * Workers never touch UI. Every queued completion rechecks its epoch; view jobs also check revision.
 * Solver cancellation keeps the epoch/Future; preparation cancellation invalidates even queued deliveries.
 */
public final class OperationalController implements AutoCloseable {
    private final ExecutorService compute, presentation;
    private final Consumer<Runnable> dispatch;
    private final OperationalServices services;
    private final Consumer<OperationalSnapshot> listener;
    private long epoch, instanceId, runId, revision;
    private boolean closed, configurationReady, executeWhenReady;
    private enum Work { IDLE, PREPARATION, SOLVER }
    private Work work = Work.IDLE;
    private GenerationCancellationToken generation = new GenerationCancellationToken();
    private SolverCancellationToken tspToken = new SolverCancellationToken();
    private ShortestPathCancellationToken pathToken = new ShortestPathCancellationToken();
    private TspProblem problem;
    private PreparedWorld world;
    private WorldSceneModel scene;
    private RefugeGraph graph;
    private ExecutionRequest request = ExecutionRequest.defaults();
    private ExecutionRecord result;
    private final List<ExecutionRecord> history = new ArrayList<>();
    private int n, destination = 1;
    private long seed;
    private OperationalSnapshot.Phase phase = SEM_INSTANCIA, presentationPhase;
    private String stage = "", preflight = "", diagnostic = "";

    public OperationalController(ExecutorService compute, ExecutorService presentation, Consumer<Runnable> dispatch,
                                 OperationalServices services, Consumer<OperationalSnapshot> listener) {
        this.compute = Objects.requireNonNull(compute); this.presentation = Objects.requireNonNull(presentation);
        this.dispatch = Objects.requireNonNull(dispatch); this.services = Objects.requireNonNull(services);
        this.listener = Objects.requireNonNull(listener);
    }
    public static OperationalController create(Consumer<Runnable> dispatch, Consumer<OperationalSnapshot> listener) {
        return new OperationalController(worker("nodewalker-compute"), worker("nodewalker-presentation"), dispatch,
                new OperationalServices() { }, listener);
    }
    private static ExecutorService worker(String name) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), r -> {
            var t = new Thread(r, name); t.setDaemon(true); return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    public synchronized OperationalSnapshot snapshot() {
        return new OperationalSnapshot(phase, stage, instanceId, runId, n, seed, problem, scene, request,
                destination, result, history, preflight, diagnostic);
    }
    private void publish() { listener.accept(snapshot()); }
    private void open() { if (closed) throw new IllegalStateException("Controller closed"); }
    private void invalidate() {
        epoch++; revision++; presentationPhase = null; executeWhenReady = false; work = Work.IDLE;
        generation.cancel(); tspToken.cancel(); pathToken.cancel();
        generation = new GenerationCancellationToken();
        tspToken = new SolverCancellationToken(); pathToken = new ShortestPathCancellationToken();
        clearQueue(compute); clearQueue(presentation);
    }
    private static void clearQueue(ExecutorService executor) {
        if (executor instanceof ThreadPoolExecutor pool) pool.getQueue().clear();
    }
    private void submit(ExecutorService executor, Runnable action) {
        try { executor.execute(action); }
        catch (RejectedExecutionException failure) { phase = ERRO; stage = "AGENDAMENTO"; diagnostic = failure.toString(); publish(); }
    }
    private synchronized boolean current(long expected) { return !closed && epoch == expected; }
    private void post(long expected, Runnable action) {
        dispatch.accept(() -> { synchronized (this) { if (!closed && epoch == expected) action.run(); } });
    }
    private void failed(long expected, String source, RuntimeException failure) {
        post(expected, () -> {
            phase = failure instanceof GenerationCancelledException || failure instanceof CancellationException ? CANCELADO : ERRO;
            work = Work.IDLE; presentationPhase = null;
            stage = source; diagnostic = source + ": " + failure.getClass().getSimpleName() + ": " + failure.getMessage(); publish();
        });
    }
    public synchronized void generate(int count, long instanceSeed) {
        open();
        // Admission before allocating the matrix, world or large UI lists.
        String estimate = OperationalPreflight.generation(count);
        invalidate();
        request = new ExecutionRequest(request.algorithm(), Math.min(request.origin(), count - 1), request.topology(),
                request.topologySeed(), Math.min(request.extraConnections(), count - 2), request.tspOptions(), request.pathOptions());
        instanceId++; n = count; seed = instanceSeed; destination = Math.min(destination, count - 1);
        problem = null; world = null; scene = null; graph = null; result = null; history.clear();
        configurationReady = false; work = Work.PREPARATION;
        phase = GERANDO_INSTANCIA; stage = "GERAÇÃO"; diagnostic = ""; preflight = estimate; publish();
        long current = epoch;
        var token = generation;
        submit(compute, () -> {
            if (!current(current)) return;
            try {
                checkpoint(token);
                var generated = services.generate(count, instanceSeed,
                        new GenerationOptions(DemoCatalog.GENERATION_CANDIDATES, DemoCatalog.GENERATION_BYTES, token));
                checkpoint(token);
                post(current, () -> {
                    stage = "PREPARAÇÃO VISUAL"; publish();
                    submit(presentation, () -> {
                        if (!current(current)) return;
                        try {
                            var prepared = services.prepareWorld(generated, () -> checkpoint(token));
                            checkpoint(token);
                            if (prepared.problem() != generated) throw new IllegalStateException("World prepared for a different instance");
                            post(current, () -> {
                                problem = generated; world = prepared;
                                phase = INSTANCIA_PRONTA; stage = "";
                                prepareConfiguration();
                            });
                        } catch (RuntimeException failure) { failed(current, "PREPARAÇÃO VISUAL", failure); }
                    });
                });
            } catch (RuntimeException failure) { failed(current, "GERAÇÃO", failure); }
        });
    }
    private static void checkpoint(GenerationCancellationToken token) {
        if (token.isCancellationRequested() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Preparação cancelada");
    }
    public synchronized void configure(ExecutionRequest next) {
        open(); Objects.requireNonNull(next);
        if (problem != null) next.validateFor(problem.size());
        if (request.equals(next)) {
            if (world != null && !configurationReady && !phase.busy()) prepareConfiguration();
            return;
        }
        invalidate(); request = next; graph = null; result = null; diagnostic = ""; configurationReady = false;
        if (world != null) { phase = INSTANCIA_PRONTA; prepareConfiguration(); }
        else { phase = SEM_INSTANCIA; publish(); }
    }
    private void invalidateScene() {
        scene = world == null ? null : new PendingSceneModel(world);
    }
    private void prepareConfiguration() {
        work = Work.PREPARATION; diagnostic = ""; invalidateScene();
        if (request.algorithm().tsp()) {
            preflight = OperationalPreflight.tsp(n, request); phase = VALIDANDO_EXECUCAO; stage = "APRESENTAÇÃO";
            presentationPhase = INSTANCIA_PRONTA; publish(); prepareScene();
        } else {
            phase = VALIDANDO_EXECUCAO; stage = "TOPOLOGIA / PREFLIGHT"; publish();
            long current = epoch; var configured = request; var input = problem; var token = generation;
            submit(compute, () -> {
                if (!current(current)) return;
                try {
                    configured.validateFor(input.size()); checkpoint(token);
                    var prepared = services.prepareGraph(input, configured, token);
                    checkpoint(token);
                    if (prepared.problem() != input) throw new IllegalStateException("Topology prepared for a different instance");
                    String report = OperationalPreflight.paths(prepared, configured);
                    post(current, () -> { graph = prepared; preflight = report; presentationPhase = INSTANCIA_PRONTA; stage = "APRESENTAÇÃO"; prepareScene(); });
                } catch (RuntimeException failure) { failed(current, "TOPOLOGIA / PREFLIGHT", failure); }
            });
        }
    }
    public synchronized void destination(int next) {
        open(); if (problem == null) throw new IllegalStateException("Sem instância");
        Objects.checkIndex(next, problem.size());
        if (destination == next) return;
        destination = next;
        if (!request.algorithm().tsp() && graph != null) {
            invalidateScene(); publish(); prepareScene();
        } else publish();
    }
    private void prepareScene() {
        long current = epoch, view = ++revision;
        var prepared = world; var previousScene = scene; var active = result; var configured = request; var available = graph; int target = destination;
        clearQueue(presentation);
        submit(presentation, () -> {
            if (!current(current)) return;
            try {
                WorldSceneModel next = configured.algorithm().tsp()
                        ? previousScene instanceof TspSceneModel tsp && tsp.preparedWorld() == prepared
                            ? tsp.withResult(active instanceof ExecutionRecord.Tsp r ? Optional.of(r.result()) : Optional.empty())
                            : TspSceneModel.of(prepared, active instanceof ExecutionRecord.Tsp r ? Optional.of(r.result()) : Optional.empty())
                        : new ShortestPathSceneModel(prepared, available, configured.origin(), target,
                        active instanceof ExecutionRecord.Paths r ? Optional.of(r.result()) : Optional.empty());
                post(current, () -> { if (view == revision) {
                    scene = next;
                    if (presentationPhase != null) {
                        phase = presentationPhase; presentationPhase = null;
                        if (phase == INSTANCIA_PRONTA) configurationReady = true;
                        work = Work.IDLE; stage = phase == INSTANCIA_PRONTA ? "" : "SOLVER";
                    }
                    publish();
                    if (executeWhenReady && phase == INSTANCIA_PRONTA) { executeWhenReady = false; execute(); }
                } });
            } catch (RuntimeException failure) { post(current, () -> { if (view == revision) { phase = ERRO; work = Work.IDLE; presentationPhase = null; stage = "APRESENTAÇÃO"; diagnostic = failure.toString(); publish(); } }); }
        });
    }
    public synchronized void execute() {
        open();
        if (world == null || phase.busy()) throw new IllegalStateException("Instância/configuração ainda não está pronta");
        request.validateFor(n);
        if (!configurationReady) {
            executeWhenReady = true; prepareConfiguration(); return;
        }
        if (!request.algorithm().tsp() && graph == null) throw new IllegalStateException("Topologia ainda não está pronta");
        // Every run gets a new identity, including reruns on the same matrix.
        invalidate(); runId++; result = null; diagnostic = ""; work = Work.SOLVER;
        invalidateScene();
        phase = VALIDANDO_EXECUCAO; stage = "PREFLIGHT"; publish();
        long current = epoch, instance = instanceId, run = runId;
        var input = problem; var preparedWorld = world; var available = graph; var configured = request; var tsp = tspToken; var paths = pathToken;
        prepareScene();
        submit(compute, () -> {
            if (!current(current)) return;
            try {
                String report = configured.algorithm().tsp() ? OperationalPreflight.tsp(input.size(), configured)
                        : OperationalPreflight.paths(available, configured);
                post(current, () -> { preflight = report; if (phase != CANCELAMENTO_SOLICITADO) phase = EXECUTANDO_ALGORITMO;
                    stage = "SOLVER"; publish(); });
                ExecutionRecord completed = configured.algorithm().tsp()
                        ? new ExecutionRecord.Tsp(instance, run, configured, services.tsp(input, configured, tsp))
                        : new ExecutionRecord.Paths(instance, run, configured, services.paths(available, configured, paths));
                if (completed instanceof ExecutionRecord.Tsp t) TspSceneModel.of(preparedWorld, Optional.of(t.result()));
                else if (completed instanceof ExecutionRecord.Paths p && (p.result().graph() != available || p.result().origin() != configured.origin()))
                    throw new IllegalStateException("Solver returned a different graph/origin");
                post(current, () -> {
                    result = completed;
                    presentationPhase = OperationalSnapshot.Phase.valueOf(completed.status()); stage = "APRESENTAÇÃO";
                    history.add(completed); while (history.size() > 8) history.removeFirst();
                    prepareScene();
                });
            } catch (RuntimeException failure) { failed(current, "SOLVER / PREFLIGHT", failure); }
        });
    }
    public synchronized void cancel() {
        open(); if (!phase.busy()) return;
        if (work == Work.PREPARATION) {
            String cancelledStage = stage;
            phase = CANCELAMENTO_SOLICITADO; publish();
            // Preparation has no solver response to preserve. Invalidate even callbacks already queued for UI.
            invalidate(); configurationReady = false; graph = null;
            phase = CANCELADO; stage = cancelledStage;
            diagnostic = cancelledStage + ": preparação cancelada; nenhuma busca iniciada.";
            publish();
        } else if (work == Work.SOLVER) {
            // Keep epoch/revision: the solver owns the terminal status, including a completed late response.
            tspToken.cancel(); pathToken.cancel();
            phase = CANCELAMENTO_SOLICITADO; publish();
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        invalidate(); closed = true; compute.shutdownNow(); presentation.shutdownNow();
    }
}
