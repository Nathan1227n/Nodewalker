package io.github.nathan1227n.nodewalker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CliOptionsTest {
    @Test
    void parsesRequiredOptionsAndDocumentedDefaults() {
        CliOptions options = CliOptions.parse(args());

        assertEquals(new CliOptions(4, 657, "brute-force", 3, 5, 10_000_000, 100_000, 67_108_864), options);
        assertEquals(8L, options.calls());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE})
    void acceptsEveryRepresentableSeedIncludingNegativeValues(long seed) {
        assertEquals(seed, CliOptions.parse(new String[] {"--seed", Long.toString(seed), "--solver", "brute-force",
                "--n", "3"}).seed());
    }

    @Test
    void acceptsExplicitInclusiveLimitsAndSingleExecution() {
        CliOptions options = CliOptions.parse(args("--warmup", "0", "--runs", "1", "--max-total-routes", "1",
                "--max-candidates", "1", "--max-estimated-bytes", "1"));

        assertEquals(0, options.warmup());
        assertEquals(1, options.runs());
        assertEquals(1L, options.calls());
        assertEquals(1L, options.maxTotalRoutes());
        assertEquals(1L, options.maxCandidates());
        assertEquals(1L, options.maxEstimatedBytes());
    }

    @Test
    void countsCallsWithoutIntegerOverflow() {
        CliOptions options = CliOptions.parse(args("--warmup", "2147483647", "--runs", "2147483647",
                "--max-total-routes", Long.toString(Long.MAX_VALUE)));

        assertEquals(4_294_967_294L, options.calls());
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void rejectsInvalidOrAmbiguousArguments(String[] arguments, String diagnostic) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(arguments));

        assertTrue(error.getMessage().contains(diagnostic), error.getMessage());
    }

    private static Stream<Arguments> invalidArguments() {
        List<Arguments> cases = new ArrayList<>();
        cases.add(Arguments.of(new String[0], "--n"));
        cases.add(Arguments.of(new String[] {"--n", "4"}, "--seed"));
        cases.add(Arguments.of(new String[] {"--n", "4", "--seed", "0"}, "--solver"));
        cases.add(Arguments.of(args("--mystery", "1"), "desconhecida"));
        cases.add(Arguments.of(args("--runs"), "Falta valor"));
        cases.add(Arguments.of(args("--runs", "--warmup", "1"), "Falta valor"));
        cases.add(Arguments.of(args("--runs", " "), "Falta valor"));
        cases.add(Arguments.of(args("--runs", "1", "--runs", "2"), "repetida"));
        cases.add(Arguments.of(args("extra"), "desconhecida"));
        cases.add(Arguments.of(new String[] {"--n", "4", "--seed", "0", "--solver", "backtracking"}, "indisponível"));
        cases.add(Arguments.of(new String[] {"--n", "4", "--seed", "0", "--solver", "unknown"}, "indisponível"));
        for (String n : List.of("2", "0", "-1", "2147483648", "abc", "3.5")) {
            cases.add(Arguments.of(new String[] {"--n", n, "--seed", "0", "--solver", "brute-force"}, "--n"));
        }
        for (String seed : List.of("9223372036854775808", "-9223372036854775809", "abc", "1.5")) {
            cases.add(Arguments.of(new String[] {"--n", "3", "--seed", seed, "--solver", "brute-force"}, "--seed"));
        }
        for (String flag : List.of("--warmup", "--runs")) {
            for (String value : List.of("-1", "2147483648", "abc")) {
                cases.add(Arguments.of(args(flag, value), flag));
            }
        }
        cases.add(Arguments.of(args("--runs", "0"), "--runs"));
        for (String flag : List.of("--max-total-routes", "--max-candidates", "--max-estimated-bytes")) {
            for (String value : List.of("0", "-1", "9223372036854775808", "abc")) {
                cases.add(Arguments.of(args(flag, value), flag));
            }
        }
        return cases.stream();
    }

    static String[] args(String... extra) {
        List<String> arguments = new ArrayList<>(List.of("--n", "4", "--seed", "657", "--solver", "brute-force"));
        arguments.addAll(Arrays.asList(extra));
        return arguments.toArray(String[]::new);
    }
}
