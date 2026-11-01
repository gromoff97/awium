package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.WaitCompletion;
import io.github.gromoff97.awium.support.FakeTime;
import java.io.IOException;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.execution.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.execution.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitEngineLifecycleTest {
    private final FakeTime time = new FakeTime(0);
    private final ConditionRun<String, String> run = mock();

    @AfterEach
    void clearInterruptFlag() { interrupted(); }

    @Test
    void interruptedPreparationSkipsSourceRetrieval() throws Exception {
        Callable<String> source = mock();
        doAnswer(ignored -> {
            currentThread().interrupt();
            return null;
        }).when(run).beginAttempt();

        var outcome = assertInstanceOf(Uncontrolled.class, execute(source));
        var attempt = assertInstanceOf(FailedBeforeObservation.class, outcome.step().attempt());
        assertEquals(CONDITION, attempt.operation());
        verify(source, never()).call();
        verify(run).beginAttempt();
    }

    @Test
    void completionFailuresRetainTheObservation() throws Throwable {
        var cause = new IllegalStateException("completion failed");
        doReturn(satisfied("chosen")).when(run).probe("actual");
        when(run.complete()).thenThrow(cause);

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        var attempt = assertInstanceOf(FailedAfterObservation.class, outcome.step().attempt());
        assertEquals("actual", attempt.observed());
        assertEquals(CONDITION, attempt.operation());
        assertSame(cause, attempt.cause());
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 11})
    void completionMustFinishBeforeTheAcquisitionDeadline(long elapsed) throws Throwable {
        doReturn(satisfied("chosen")).when(run).probe("actual");
        when(run.complete()).thenAnswer(ignored -> {
            time.advanceNanos(elapsed);
            return true;
        });

        var outcome = assertInstanceOf(Timeout.class, execute(() -> "actual"));
        assertEquals(elapsed, outcome.elapsedNanos());
    }

    @Test
    void sourceFailuresDoNotAskTheConditionForCompletion() {
        var cause = new IOException("source failed");
        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> { throw cause; }));

        assertSame(cause, outcome.step().attempt().cause());
        verify(run, never()).complete();
    }

    @Test
    void probeFailuresDoNotAskTheConditionForCompletion() throws Throwable {
        var cause = new IOException("check failed");
        doThrow(cause).when(run).probe("actual");

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertSame(cause, outcome.step().attempt().cause());
        verify(run, never()).complete();
    }

    @ParameterizedTest
    @CsvSource({"probe, 0", "completion, 1"})
    void cancellationStopsTheLifecycle(String interruptAt, int completionCalls) throws Throwable {
        doAnswer(ignored -> {
            if (interruptAt.equals("probe")) currentThread().interrupt();
            return satisfied("chosen");
        }).when(run).probe("actual");
        when(run.complete()).thenAnswer(ignored -> {
            currentThread().interrupt();
            return true;
        });

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertTrue(outcome.interrupted());
        assertInstanceOf(InterruptedException.class, outcome.step().attempt().cause());
        assertTrue(currentThread().isInterrupted());
        verify(run, times(completionCalls)).complete();
    }

    @Test
    void cancellationDuringCompletionPreservesTheMismatchCause() throws Throwable {
        var cause = new AssertionError("value is still pending");
        doReturn(unsatisfied("pending", cause)).when(run).probe("actual");
        when(run.complete()).thenAnswer(ignored -> {
            currentThread().interrupt();
            return false;
        });

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertTrue(outcome.interrupted());
        assertSame(cause, outcome.step().attempt().cause());
    }

    private WaitCompletion execute(Callable<String> source) {
        return new WaitEngine<>(withTime(config(1, 10, 0), time, time), source, run)
                .waitFor(ignored -> {});
    }
}
