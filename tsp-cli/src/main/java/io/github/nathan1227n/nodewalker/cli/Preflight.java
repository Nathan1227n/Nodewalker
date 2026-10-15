package io.github.nathan1227n.nodewalker.cli;

import io.github.nathan1227n.nodewalker.core.SearchSpace;
import io.github.nathan1227n.nodewalker.core.generator.TspGenerator;
import java.math.BigInteger;

/** Trabalho teórico da bateria, separado das métricas observadas de cada resultado. */
record Preflight(BigInteger estimatedBytes, BigInteger routes, BigInteger totalRoutes) {
    static Preflight check(CliOptions options) {
        BigInteger bytes = TspGenerator.estimatePeakMemoryBytes(options.n());
        if (options.maxCandidates() < options.n()) {
            throw new IllegalArgumentException("--max-candidates=" + options.maxCandidates()
                    + " é insuficiente para N=" + options.n() + ".");
        }
        if (bytes.compareTo(BigInteger.valueOf(options.maxEstimatedBytes())) > 0) {
            throw new IllegalArgumentException("Memória de geração estimada: " + bytes
                    + " bytes; excede --max-estimated-bytes=" + options.maxEstimatedBytes() + ".");
        }

        // 21! já excede qualquer orçamento long positivo. Para N > 22 basta
        // esse limite inferior; não se constrói um fatorial gigantesco para recusar.
        BigInteger routes = SearchSpace.completeRoutes(Math.min(options.n(), 22));
        BigInteger total = routes.multiply(BigInteger.valueOf(options.calls()));
        if (total.compareTo(BigInteger.valueOf(options.maxTotalRoutes())) > 0) {
            String relation = options.n() > 22 ? ">= " : "= ";
            throw new IllegalArgumentException("Espaço teórico (N-1)! " + relation + routes
                    + "; avaliações previstas na bateria " + relation + total
                    + " excedem --max-total-routes=" + options.maxTotalRoutes()
                    + ". Reduza N, aquecimentos ou medições; o teto é configurável por --max-total-routes.");
        }
        return new Preflight(bytes, routes, total);
    }
}
