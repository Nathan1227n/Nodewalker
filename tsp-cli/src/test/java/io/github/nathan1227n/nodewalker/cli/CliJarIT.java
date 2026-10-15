package io.github.nathan1227n.nodewalker.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifica o artefato empacotado a partir de outro diretório, sem classpath Maven nem display. */
class CliJarIT {
    @TempDir
    Path temporaryDirectory;

    @Test
    void executableJarContainsCoreAndNoGraphicalOrTestDependencies() throws Exception {
        try (JarFile jar = new JarFile(jarPath().toFile())) {
            assertEquals("io.github.nathan1227n.nodewalker.cli.NodeWalkerCli",
                    jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertNotNull(jar.getEntry("io/github/nathan1227n/nodewalker/core/solver/BruteForceSolver.class"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("javafx/")
                    || entry.getName().startsWith("com/sun/javafx/")
                    || entry.getName().startsWith("io/github/nathan1227n/nodewalker/app/")
                    || entry.getName().startsWith("org/junit/")));
        }
    }

    @Test
    void runsTheKnownReferenceWithNoDisplayOrExternalClasspath() throws Exception {
        Output output = execute("--n", "4", "--seed", "657", "--solver", "brute-force", "--warmup", "1", "--runs", "3");

        assertEquals(0, output.exit(), output.err());
        assertEquals("", output.err());
        assertTrue(output.out().contains("Benchmark: CONCLUIDO"));
        assertTrue(output.out().contains("Espaço teórico (N-1)!: 6"));
        assertTrue(output.out().contains("medições): 24"));
        assertTrue(output.out().contains("Seed da instância: 657"));
        assertTrue(output.out().contains("Rota: 0 -> 2 -> 1 -> 3 -> 0"));
        assertTrue(output.out().contains("Custo: 2799"));
        assertTrue(output.out().contains("completeRoutesEvaluated: 6"));
        assertTrue(output.out().matches("(?s).*  elapsedNanos: [0-9]+\\R.*"));
        assertTrue(output.out().contains("visitedNodes: 6"));
        assertTrue(output.out().contains("discardedRoutes: 0"));
        assertTrue(output.out().contains("Mediana do solver (ns): "));
    }

    @Test
    void printsStandaloneHelp() throws Exception {
        Output output = execute("--help");

        assertEquals(0, output.exit());
        assertTrue(output.out().contains("Uso: java -jar"));
        assertFalse(output.out().contains("Estado do solver"));
    }

    @Test
    void returnsAnErrorCodeForUnimplementedSolvers() throws Exception {
        Output output = execute("--n", "4", "--seed", "0", "--solver", "backtracking");

        assertEquals(2, output.exit());
        assertTrue(output.err().contains("Apenas brute-force está implementado"));
        assertFalse(output.out().contains("Tempo de geração"));
    }

    @Test
    void refusesUnrepresentableWorkWithoutStartingGeneration() throws Exception {
        Output output = execute("--n", "22", "--seed", "0", "--solver", "brute-force",
                "--max-total-routes", "9223372036854775807");

        assertEquals(2, output.exit());
        assertTrue(output.err().contains("(N-1)! = 51090942171709440000"));
        assertFalse(output.out().contains("Tempo de geração"));
        assertFalse(output.out().contains("Estado do solver"));
    }

    private Output execute(String... arguments) throws Exception {
        String javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", javaName).toString(),
                "-Dfile.encoding=UTF-8", "-Djava.awt.headless=true", "-jar", jarPath().toString()));
        command.addAll(Arrays.asList(arguments));
        Path stdout = temporaryDirectory.resolve("stdout.txt");
        Path stderr = temporaryDirectory.resolve("stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporaryDirectory.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().remove("DISPLAY");
        builder.environment().remove("WAYLAND_DISPLAY");
        builder.environment().remove("CLASSPATH");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "A CLI não terminou dentro do prazo do teste.");
            return new Output(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Path jarPath() {
        return Path.of(System.getProperty("cli.jar")).toAbsolutePath();
    }

    private record Output(int exit, String out, String err) {
    }
}
