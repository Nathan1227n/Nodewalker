package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.core.solver.TspResult;
import io.github.nathan1227n.nodewalker.core.shortestpath.ShortestPathResult;
import java.util.Objects;

/** Typed history entries; no common route, cost or optimality contract between the problems. */
public sealed interface ExecutionRecord permits ExecutionRecord.Tsp, ExecutionRecord.Paths {
    long instanceId();
    long runId();
    ExecutionRequest request();
    String status();
    record Tsp(long instanceId, long runId, ExecutionRequest request, TspResult result) implements ExecutionRecord {
        public Tsp { Objects.requireNonNull(request); Objects.requireNonNull(result); }
        @Override public String status() { return result.status().name(); }
    }
    record Paths(long instanceId, long runId, ExecutionRequest request, ShortestPathResult result) implements ExecutionRecord {
        public Paths { Objects.requireNonNull(request); Objects.requireNonNull(result); }
        @Override public String status() { return result.status().name(); }
    }
}
