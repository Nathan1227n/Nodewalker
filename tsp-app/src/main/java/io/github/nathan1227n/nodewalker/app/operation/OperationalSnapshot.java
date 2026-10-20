package io.github.nathan1227n.nodewalker.app.operation;

import io.github.nathan1227n.nodewalker.app.world.*;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.List;

/** Immutable publication. Null instance/scene/result means absent, never a numeric sentinel. */
public record OperationalSnapshot(Phase phase, String stage, long instanceId, long runId, int n, long seed,
                                  TspProblem problem, WorldSceneModel scene, ExecutionRequest request,
                                  int destination, ExecutionRecord result, List<ExecutionRecord> history,
                                  String preflight, String diagnostic) {
    public OperationalSnapshot { history = List.copyOf(history); }
    public enum Phase {
        SEM_INSTANCIA, GERANDO_INSTANCIA, INSTANCIA_PRONTA, VALIDANDO_EXECUCAO,
        EXECUTANDO_ALGORITMO, CANCELAMENTO_SOLICITADO, CONCLUIDO, CANCELADO, LIMITE_ATINGIDO, ERRO;
        public String label() { return name().replace('_', ' '); }
        public boolean busy() { return this == GERANDO_INSTANCIA || this == VALIDANDO_EXECUCAO
                || this == EXECUTANDO_ALGORITMO || this == CANCELAMENTO_SOLICITADO; }
    }
}
