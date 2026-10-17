package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;

/**
 * Contrato comum de entrada/saída para os futuros solvers exatos, sem dependência gráfica.
 * A busca deve explorar índices em ordem lexicográfica e conservar a primeira ótima:
 * somente custo estritamente menor substitui o incumbente (DOC 03 §6).
 * A entrada simples conserva o contrato legado sem limites automáticos.
 */
@FunctionalInterface
public interface TspSolver {
    /**
     * Resolve o problema e retorna um estado terminal com métricas reportadas.
     * CONCLUIDO exige busca terminada corretamente, ótimo global e rota canônica;
     * validar uma rota por si só não satisfaz essa garantia.
     * Os solvers devem tratar falhas de execução, inclusive overflow, como ERRO,
     * sem reportar sucesso ou substituir um custo por sentinela de infinito.
     * Cancelamento/limite podem retornar um ciclo incumbente completo ou nenhuma rota.
     *
     * @param problem instância válida e imutável com origem fixa 0
     * @return resultado final não nulo
     * @throws NullPointerException se problem for nulo
     */
    TspResult solve(TspProblem problem);

    /**
     * Executa com preflight operacional, limites cooperativos e token separado da geração.
     * Implementações existentes continuam funcionais; precisam implementar explicitamente
     * esta sobrecarga para suportar controles. Argumentos inválidos/preflight recusado falham
     * antes da busca. Nos solvers do núcleo, falhas de execução resultam em ERRO.
     *
     * @throws UnsupportedOperationException se esta implementação não suporta controles
     */
    default TspResult solve(TspProblem problem, SolverExecutionOptions options, SolverCancellationToken token) {
        throw new UnsupportedOperationException("Esta implementação de TspSolver não suporta execução controlada.");
    }
}
