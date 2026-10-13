package io.github.nathan1227n.nodewalker.core.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nathan1227n.nodewalker.core.model.TspProblem;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(15)
class TspGenerationCancellationTest {
    @Test
    void preCancelledTokenStopsBeforeDrawingAnyCandidateAndRemainsCancelledOnReuse() {
        GenerationOptions options = new GenerationOptions(5, 4096);
        options.cancellationToken().cancel();

        for (int repeat = 0; repeat < 2; repeat++) {
            GenerationCancelledException error = assertThrows(GenerationCancelledException.class,
                    () -> TspGenerator.generate(4, 657L, options));

            assertEquals(0L, error.attemptedCandidates());
            assertEquals(0, error.acceptedCities());
        }
        assertTrue(options.cancellationToken().isCancellationRequested());
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "2, 1", "3, 2"})
    void cancellationAfterDrawingACandidateCountsTheWholeTrioButNotAnUnclassifiedCity(long stopAt, int accepted) {
        GenerationOptions options = new GenerationOptions(5, 4096);
        AtomicReference<TspProblem> returned = new AtomicReference<>();

        GenerationCancelledException error = assertThrows(GenerationCancelledException.class,
                () -> returned.set(TspGenerator.generate(4, 657L, options, (phase, attempts, count) -> {
                    if (phase == TspGenerator.Phase.CITIES && attempts == stopAt) {
                        options.cancellationToken().cancel();
                    }
                })));

        assertEquals(stopAt, error.attemptedCandidates());
        assertEquals(accepted, error.acceptedCities());
        assertNull(returned.get());
    }

    @ParameterizedTest
    @EnumSource(value = TspGenerator.Phase.class,
            names = {"MATRIX_ALLOCATION", "MATRIX_COSTS", "FINALIZATION", "READY"})
    void cancellationDuringMatrixOrProblemConstructionNeverReturnsTheCompletedInstance(TspGenerator.Phase target) {
        GenerationOptions options = new GenerationOptions(5, 4096);
        AtomicReference<TspProblem> returned = new AtomicReference<>();
        AtomicInteger targetCheckpoints = new AtomicInteger();
        int cancelAt = switch (target) {
            case MATRIX_ALLOCATION, FINALIZATION -> 3;
            case MATRIX_COSTS -> 2;
            default -> 1;
        };

        GenerationCancelledException error = assertThrows(GenerationCancelledException.class,
                () -> returned.set(TspGenerator.generate(4, 657L, options, (phase, attempts, accepted) -> {
                    if (phase == target && targetCheckpoints.incrementAndGet() == cancelAt) {
                        options.cancellationToken().cancel();
                    }
                })));

        assertEquals(5L, error.attemptedCandidates());
        assertEquals(4, error.acceptedCities());
        assertEquals(cancelAt, targetCheckpoints.get());
        assertNull(returned.get());
    }

    @Test
    void interruptedThreadIsCancelledBeforeGenerationWithoutClearingItsFlag() {
        boolean previouslyInterrupted = Thread.currentThread().isInterrupted();
        try {
            Thread.currentThread().interrupt();
            GenerationCancelledException error = assertThrows(GenerationCancelledException.class,
                    () -> TspGenerator.generate(4, 657L, new GenerationOptions(5, 4096)));

            assertEquals(0L, error.attemptedCandidates());
            assertEquals(0, error.acceptedCities());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            if (!previouslyInterrupted) {
                Thread.interrupted();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anotherThreadCanCancelAtAnExplicitBarrierWithoutSleeping(boolean byInterrupt) throws InterruptedException {
        GenerationOptions options = new GenerationOptions(5, 4096);
        CountDownLatch reached = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean paused = new AtomicBoolean();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        AtomicReference<TspProblem> returned = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                returned.set(TspGenerator.generate(4, 657L, options, (phase, attempts, accepted) -> {
                    if (phase == TspGenerator.Phase.CITIES && attempts == 2 && accepted == 2
                            && paused.compareAndSet(false, true)) {
                        reached.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException interruption) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }));
            } catch (RuntimeException | Error error) {
                failure.set(error);
                interruptPreserved.set(Thread.currentThread().isInterrupted());
            } finally {
                finished.countDown();
            }
        }, "tsp-generation-cancellation-test");
        worker.setDaemon(true);
        worker.start();
        try {
            assertTrue(reached.await(5, TimeUnit.SECONDS), "O worker deve atingir a barreira determinística.");
            if (byInterrupt) {
                worker.interrupt();
            } else {
                options.cancellationToken().cancel();
                release.countDown();
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS), "A geração deve observar o cancelamento.");

            GenerationCancelledException error = assertInstanceOf(GenerationCancelledException.class, failure.get());
            assertEquals(2L, error.attemptedCandidates());
            assertEquals(2, error.acceptedCities());
            assertNull(returned.get());
            assertEquals(byInterrupt, interruptPreserved.get());
            if (byInterrupt) {
                assertFalse(options.cancellationToken().isCancellationRequested());
            }
        } finally {
            options.cancellationToken().cancel();
            release.countDown();
            worker.interrupt();
            worker.join(5000);
            assertFalse(worker.isAlive(), "O worker de teste deve terminar.");
        }
    }

    @Test
    void realFailuresArePropagatedInsteadOfBecomingCancellationOrLimit() {
        IllegalStateException realFailure = new IllegalStateException("Falha real de teste");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TspGenerator.generate(4, 657L, new GenerationOptions(5, 4096), (phase, attempts, accepted) -> {
                    if (phase == TspGenerator.Phase.MATRIX_COSTS) {
                        throw realFailure;
                    }
                }));

        assertSame(realFailure, error);
    }
}
