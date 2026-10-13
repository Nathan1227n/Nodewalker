package io.github.nathan1227n.nodewalker.core.solver;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import io.github.nathan1227n.nodewalker.core.validation.RouteValidator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Resultado final imutável de um solver, construído somente por fábricas validadas.
 * Uma rota presente é sempre um ciclo completo incumbente; uma execução parcial
 * não representa um trecho incompleto de rota nem uma prova de ótimo global.
 * Long.MAX_VALUE é um custo válido, nunca uma sentinela de ausência ou infinito.
 */
public final class TspResult {
    private final SolverStatus status;
    private final Optional<TspRoute> route;
    private final OptionalLong cost;
    private final SolverMetrics metrics;
    private final Optional<String> reason;

    private TspResult(SolverStatus status, Optional<TspRoute> route, OptionalLong cost,
                      SolverMetrics metrics, Optional<String> reason) {
        this.status = status;
        this.route = route;
        this.cost = cost;
        this.metrics = metrics;
        this.reason = reason;
    }

    /**
     * Constrói um resultado CONCLUIDO com ciclo canônico e custo recalculado consistente.
     * A chamada declara que a busca terminou corretamente e provou o ótimo global;
     * essa responsabilidade é do solver. Esta fábrica valida viabilidade, orientação
     * e custo, mas não executa busca nem prova otimalidade por si só.
     * A razão de interrupção fica ausente.
     *
     * @param problem problema cuja matriz define o custo
     * @param route ciclo completo em orientação canônica
     * @param declaredCost custo reportado pelo solver, igual ao recalculado
     * @param metrics snapshot de métricas reportadas
     * @return resultado concluído validado
     * @throws NullPointerException se algum argumento de referência for nulo
     * @throws IllegalArgumentException se rota, orientação ou custo violarem o contrato
     * @throws ArithmeticException se o custo recalculado sofrer overflow
     */
    public static TspResult completed(TspProblem problem, TspRoute route,
                                      long declaredCost, SolverMetrics metrics) {
        Objects.requireNonNull(route, "A rota concluída não pode ser nula.");
        Objects.requireNonNull(metrics, "As métricas não podem ser nulas.");
        if (!route.isCanonical()) {
            throw new IllegalArgumentException("Um resultado CONCLUIDO deve possuir rota canônica: route[1] < route[N-1].");
        }
        Optional<TspRoute> incumbent = Optional.of(route);
        OptionalLong cost = OptionalLong.of(declaredCost);
        validateIncumbent(problem, incumbent, cost);
        return new TspResult(SolverStatus.CONCLUIDO, incumbent, cost, metrics, Optional.empty());
    }

    /**
     * Constrói resultado CANCELADO, LIMITE_ATINGIDO ou ERRO, sem garantia de ótimo.
     * Rota e custo devem estar ambos presentes ou ambos ausentes. Uma rota presente
     * é um ciclo completo válido com custo consistente, em qualquer orientação.
     * O motivo é obrigatório e imutável; nenhum Throwable mutável é armazenado.
     * Falhas de overflow durante a validação são propagadas, nunca convertidas em sucesso.
     *
     * @param problem problema da execução, mesmo quando não há incumbente
     * @param status estado terminal de interrupção ou erro
     * @param route ciclo incumbente, se encontrado
     * @param declaredCost custo do incumbente, se encontrado
     * @param metrics snapshot de métricas reportadas, possivelmente parciais
     * @param reason diagnóstico não vazio da interrupção ou do erro
     * @return resultado interrompido validado
     * @throws NullPointerException se algum argumento de referência for nulo
     * @throws IllegalArgumentException se estado, motivo ou incumbente violarem o contrato
     * @throws ArithmeticException se o custo recalculado do incumbente sofrer overflow
     */
    public static TspResult interrupted(TspProblem problem, SolverStatus status,
                                       Optional<TspRoute> route, OptionalLong declaredCost,
                                       SolverMetrics metrics, String reason) {
        Objects.requireNonNull(status, "O estado não pode ser nulo.");
        Objects.requireNonNull(metrics, "As métricas não podem ser nulas.");
        Objects.requireNonNull(reason, "O motivo não pode ser nulo.");
        if (!status.isTerminal() || status == SolverStatus.CONCLUIDO) {
            throw new IllegalArgumentException("O estado de interrupção deve ser CANCELADO, LIMITE_ATINGIDO ou ERRO: "
                    + status + ".");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("O motivo de interrupção ou erro não pode ser vazio.");
        }
        validateIncumbent(problem, route, declaredCost);
        return new TspResult(status, route, declaredCost, metrics, Optional.of(reason));
    }

    private static void validateIncumbent(TspProblem problem, Optional<TspRoute> route, OptionalLong cost) {
        Objects.requireNonNull(problem, "O problema TSP não pode ser nulo.");
        Objects.requireNonNull(route, "O opcional de rota não pode ser nulo.");
        Objects.requireNonNull(cost, "O opcional de custo não pode ser nulo.");
        if (route.isPresent() != cost.isPresent()) {
            throw new IllegalArgumentException("Rota e custo devem estar ambos presentes ou ambos ausentes.");
        }
        if (route.isPresent()) {
            long declared = cost.orElseThrow();
            if (declared < 0) {
                throw new IllegalArgumentException("O custo declarado deve ser não negativo: " + declared + ".");
            }
            long actual = RouteValidator.validateAndCost(problem, route.get());
            if (declared != actual) {
                throw new IllegalArgumentException("O custo declarado " + declared
                        + " difere do custo recalculado " + actual + ".");
            }
        }
    }

    /** @return estado terminal da execução, separado do playback */
    public SolverStatus status() {
        return status;
    }

    /** @return ciclo completo incumbente, ausente somente em resultados interrompidos sem incumbente */
    public Optional<TspRoute> route() {
        return route;
    }

    /** @return custo validado da rota, presente exatamente quando route() estiver presente */
    public OptionalLong cost() {
        return cost;
    }

    /** @return snapshot imutável de métricas, sem inferir valores não reportados */
    public SolverMetrics metrics() {
        return metrics;
    }

    /** @return motivo obrigatório para interrupção/erro e ausente em CONCLUIDO */
    public Optional<String> reason() {
        return reason;
    }

    /**
     * @return se o solver declarou conclusão com garantia global de ótimo;
     * não significa que esta classe tenha executado uma prova independente
     */
    public boolean optimalityGuaranteed() {
        return status == SolverStatus.CONCLUIDO;
    }
}
