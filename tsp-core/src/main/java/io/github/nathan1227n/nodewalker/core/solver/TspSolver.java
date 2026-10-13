package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;

/**
 * Contrato comum de entrada/saída para os futuros solvers exatos, sem dependência gráfica.
 * A busca deve explorar índices em ordem lexicográfica e conservar a primeira ótima:
 * somente custo estritamente menor substitui o incumbente (DOC 03 §6).
 * Limites, token de cancelamento e listeners serão integrados na issue #9 por
 * uma sobrecarga, preservando esta entrada simples; não fazem parte desta entrega.
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
}
