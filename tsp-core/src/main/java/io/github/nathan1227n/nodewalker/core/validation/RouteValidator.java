package io.github.nathan1227n.nodewalker.core.validation;

import io.github.nathan1227n.nodewalker.core.model.CostMath;
import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import io.github.nathan1227n.nodewalker.core.model.TspRoute;
import java.util.Objects;

/** Validação de compatibilidade e custo de ciclos, independente de geometria e busca. */
public final class RouteValidator {
    private RouteValidator() {
    }

    /**
     * Confirma o ciclo em relação ao problema e soma todas as N arestas da matriz,
     * inclusive o retorno à origem, usando {@link CostMath#addExact(long, long)}.
     * A validade estrutural já é garantida por {@link TspRoute}.
     * Ambas as orientações são aceitas; validar um ciclo não prova seu ótimo global.
     *
     * @param problem instância cuja matriz define os custos
     * @param route ciclo completo, incluindo o zero final
     * @return custo completo não negativo, inclusive Long.MAX_VALUE quando representável
     * @throws NullPointerException se problem ou route for nulo
     * @throws IllegalArgumentException se a quantidade de cidades for incompatível
     * @throws ArithmeticException se a soma ultrapassar Long.MAX_VALUE
     */
    public static long validateAndCost(TspProblem problem, TspRoute route) {
        Objects.requireNonNull(problem, "O problema TSP não pode ser nulo.");
        Objects.requireNonNull(route, "A rota não pode ser nula.");
        if (route.cityCount() != problem.size()) {
            throw new IllegalArgumentException("A rota deve ter " + (problem.size() + 1L)
                    + " posições para " + problem.size() + " cidades, mas tem " + route.indices().size() + ".");
        }
        long cost = 0;
        for (int i = 0; i < route.cityCount(); i++) {
            cost = CostMath.addExact(cost, problem.cost(route.indices().get(i), route.indices().get(i + 1)));
        }
        return cost;
    }
}
