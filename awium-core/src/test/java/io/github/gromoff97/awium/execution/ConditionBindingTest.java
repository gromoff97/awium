package io.github.gromoff97.awium.execution;


import io.github.gromoff97.awium.Condition;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.function.AwaitAttempt.bind;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConditionBindingTest {
    private final Condition<String, String> condition = mock();
    private final Condition<String, String> run = mock(CALLS_REAL_METHODS);
    private final IOException cause = new IOException("check interrupted");

    @AfterEach
    void clearInterruptFlag() { interrupted(); }

    @Test
    void anInterruptedProbeKeepsItsCause() throws Throwable {
        when(condition.newRun()).thenReturn(run);
        doAnswer(ignored -> {
            currentThread().interrupt();
            return unsatisfied("stopped", cause);
        }).when(run).probe("actual");

        assertSame(cause, assertThrows(Throwable.class, () -> bind(condition).probe("actual")));
        assertTrue(currentThread().isInterrupted());
        verify(run, never()).historyResult(any());
    }

    @Test
    void interruptedRunCreationSkipsEvaluatorCreation() throws Throwable {
        when(condition.newRun()).thenAnswer(ignored -> {
            currentThread().interrupt();
            return run;
        });

        assertThrows(InterruptedException.class, () -> bind(condition).probe("actual"));
        assertTrue(currentThread().isInterrupted());
        verify(run, never()).newAttempt();
    }
}
