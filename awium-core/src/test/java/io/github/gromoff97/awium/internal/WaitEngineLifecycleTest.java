package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Diagnostic;
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
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
    private final Condition<String, String> run = mock(CALLS_REAL_METHODS);

    @AfterEach
    void clearInterruptFlag() { interrupted(); }

    @Test
    void historyProjectionFailuresRetainTheObservation() throws Throwable {
        var cause = new IllegalStateException("history projection failed");
        var diagnostic = new Diagnostic(null, new Reference<>("Chosen", "chosen"));
        doReturn(satisfied("chosen", diagnostic)).when(run).probe("actual");
        when(run.historyResult(any())).thenThrow(cause);

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        var attempt = assertInstanceOf(FailedAfterObservation.class, outcome.step().attempt());
        assertEquals("actual", attempt.observed());
        assertEquals(CONDITION, attempt.operation());
        assertSame(cause, attempt.cause());
        assertSame(diagnostic, attempt.diagnostic());
    }

    @ParameterizedTest
    @ValueSource(longs = {10, 11})
    void historyProjectionMustFinishBeforeTheAcquisitionDeadline(long elapsed) throws Throwable {
        doReturn(satisfied("chosen")).when(run).probe("actual");
        when(run.historyResult(any())).thenAnswer(ignored -> {
            time.advanceNanos(elapsed);
            return satisfied("chosen");
        });

        var outcome = assertInstanceOf(Timeout.class, execute(() -> "actual"));
        assertEquals(elapsed, outcome.elapsedNanos());
    }

    @Test
    void sourceFailuresDoNotProjectAHistoryResult() throws Throwable {
        var cause = new IOException("source failed");
        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> { throw cause; }));

        assertSame(cause, outcome.step().attempt().cause());
        verify(run, never()).historyResult(any());
    }

    @Test
    void probeFailuresDoNotProjectAHistoryResult() throws Throwable {
        var cause = new IOException("check failed");
        doThrow(cause).when(run).probe("actual");

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertSame(cause, outcome.step().attempt().cause());
        verify(run, never()).historyResult(any());
    }

    @ParameterizedTest
    @CsvSource({"probe, 0", "history, 1"})
    void cancellationStopsTheLifecycle(String interruptAt, int historyCalls) throws Throwable {
        doAnswer(ignored -> {
            if (interruptAt.equals("probe")) currentThread().interrupt();
            return satisfied("chosen");
        }).when(run).probe("actual");
        when(run.historyResult(any())).thenAnswer(ignored -> {
            currentThread().interrupt();
            return satisfied("chosen");
        });

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertTrue(outcome.interrupted());
        assertInstanceOf(InterruptedException.class, outcome.step().attempt().cause());
        assertTrue(currentThread().isInterrupted());
        verify(run, times(historyCalls)).historyResult(any());
    }

    @Test
    void cancellationDuringHistoryProjectionPreservesTheMismatchCause() throws Throwable {
        var cause = new AssertionError("value is still pending");
        var diagnostic = new Diagnostic("pending", new Reference<>("Progress", 2));
        doReturn(unsatisfied(diagnostic, cause)).when(run).probe("actual");
        when(run.historyResult(any())).thenAnswer(ignored -> {
            currentThread().interrupt();
            return satisfied("chosen", diagnostic);
        });

        var outcome = assertInstanceOf(Uncontrolled.class, execute(() -> "actual"));
        assertTrue(outcome.interrupted());
        assertSame(cause, outcome.step().attempt().cause());
        assertSame(diagnostic, outcome.step().attempt().diagnostic());
    }

    private WaitCompletion execute(Callable<String> source) {
        return new WaitEngine<>(withTime(config(1, 10, 0), time, time), source, new ConditionRun<>(run))
                .waitFor();
    }
}
