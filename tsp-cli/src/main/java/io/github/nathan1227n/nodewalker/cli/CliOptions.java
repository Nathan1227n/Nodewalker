package io.github.nathan1227n.nodewalker.cli;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Opções da apresentação por terminal; não introduzem limites dentro do solver. */
record CliOptions(int n, long seed, String solver, int warmup, int runs,
                  long maxTotalRoutes, long maxCandidates, long maxEstimatedBytes) {
    private static final Set<String> OPTIONS = Set.of("--n", "--seed", "--solver", "--warmup", "--runs",
            "--max-total-routes", "--max-candidates", "--max-estimated-bytes");

    CliOptions {
        if (n < 3) {
            throw new IllegalArgumentException("--n deve ser >= 3.");
        }
        if (!"brute-force".equals(solver)) {
            throw new IllegalArgumentException("Solver indisponível: " + solver
                    + ". Apenas brute-force está implementado.");
        }
        if (warmup < 0 || runs < 1) {
            throw new IllegalArgumentException("--warmup deve ser >= 0 e --runs deve ser >= 1.");
        }
        if (maxTotalRoutes <= 0 || maxCandidates <= 0 || maxEstimatedBytes <= 0) {
            throw new IllegalArgumentException("--max-total-routes, --max-candidates e --max-estimated-bytes"
                    + " devem ser long positivos.");
        }
    }

    static CliOptions parse(String[] args) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String option = args[i];
            if (!OPTIONS.contains(option)) {
                throw new IllegalArgumentException("Opção desconhecida: " + option + ". Use --help.");
            }
            if (i + 1 == args.length || args[i + 1].startsWith("--") || args[i + 1].isBlank()) {
                throw new IllegalArgumentException("Falta valor para " + option + ".");
            }
            if (values.putIfAbsent(option, args[i + 1]) != null) {
                throw new IllegalArgumentException("Opção repetida: " + option + ".");
            }
        }
        return new CliOptions(integer("--n", required(values, "--n")),
                number("--seed", required(values, "--seed")), required(values, "--solver"),
                integer("--warmup", values.getOrDefault("--warmup", "3")),
                integer("--runs", values.getOrDefault("--runs", "5")),
                number("--max-total-routes", values.getOrDefault("--max-total-routes", "10000000")),
                number("--max-candidates", values.getOrDefault("--max-candidates", "100000")),
                number("--max-estimated-bytes", values.getOrDefault("--max-estimated-bytes", "67108864")));
    }

    long calls() {
        return (long) warmup + runs;
    }

    private static String required(Map<String, String> values, String option) {
        String value = values.get(option);
        if (value == null) {
            throw new IllegalArgumentException("Opção obrigatória: " + option + ".");
        }
        return value;
    }

    private static int integer(String option, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Valor inválido para " + option + ": " + value
                    + ". Esperado um inteiro de 32 bits.", failure);
        }
    }

    private static long number(String option, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Valor inválido para " + option + ": " + value
                    + ". Esperado um inteiro de 64 bits.", failure);
        }
    }
}
