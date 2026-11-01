package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.Condition;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConditionBindingTest {
    private final Condition<String, String> condition = mock();
    private final ConditionRun<String, String> run = mock();
    private final IOException cause = new IOException("check interrupted");

    @AfterEach
    void clearInterruptFlag() { interrupted(); }

    @Test
    void interruptedPreparationSkipsTheNestedProbe() throws Throwable {
        when(condition.newRun(condition, false)).thenReturn(run);
        doAnswer(ignored -> {
            currentThread().interrupt();
            return null;
        }).when(run).beginAttempt();
        doReturn(unsatisfied("pending")).when(run).probe("actual");

        assertThrows(InterruptedException.class, () -> bind(condition).probe("actual"));
        verify(run, never()).probe("actual");
    }

    @Test
    void interruptedProbeSkipsCompletionAndKeepsItsCause() throws Throwable {
        when(condition.newRun(condition, false)).thenReturn(run);
        doAnswer(ignored -> {
            currentThread().interrupt();
            return unsatisfied("stopped", cause);
        }).when(run).probe("actual");
        when(run.complete()).thenReturn(true);

        assertSame(cause, assertThrows(Throwable.class, () -> bind(condition).probe("actual")));
        assertTrue(currentThread().isInterrupted());
        verify(run, never()).complete();
        verify(run, never()).result();
    }

    @Test
    void interruptedCompletionSkipsResultAndKeepsTheProbeCause() throws Throwable {
        when(condition.newRun(condition, false)).thenReturn(run);
        doReturn(unsatisfied("observed", cause)).when(run).probe("actual");
        when(run.complete()).thenAnswer(ignored -> {
            currentThread().interrupt();
            return true;
        });

        assertSame(cause, assertThrows(Throwable.class, () -> bind(condition).probe("actual")));
        assertTrue(currentThread().isInterrupted());
        verify(run, never()).result();
    }
}
