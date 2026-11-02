package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.support.AwaitAssertions.result;
import static io.github.gromoff97.awium.support.TestConditions.checkPerWait;
import static io.github.gromoff97.awium.function.AwaitAttempt.bind;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GuardedAttemptTest {

    @Test
    void nestedChecksKeepTheirOutcomeWithoutBuildingHistory() throws Throwable {
        Condition<String, String> condition = mock(CALLS_REAL_METHODS);
        var diagnostic = new Diagnostic("stage passed", new Reference<>("Completed checks", 1));
        var verdict = unsatisfied(diagnostic.withMismatch("waiting for another observation"));
        doReturn(verdict).when(condition).probe("actual");
        doReturn(satisfied("selected", diagnostic)).when(condition).historyResult(any());

        assertSame(verdict, bind(condition).probe("actual"));
        verify(condition, never()).historyResult(any());
    }

    @Test
    void evaluatorFactoriesAcceptBroaderInputsAndNarrowerResults() throws Throwable {
        Callable<AwaitAttempt<CharSequence, Integer>> factory = () -> actual -> satisfied(actual.length());
        var execution = new GuardedAttempt<String, Number>(factory);

        assertEquals(5, result(execution.probe("ready")));
    }

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void eachProbeReturnsAnIndependentResultAndSharesOneLazyEvaluator() throws Throwable {
        int[] factories = {0};
        var observations = new ArrayList<String>();
        Condition<String, Integer> condition = checkPerWait("count calls", () -> {
            factories[0]++;
            return actual -> {
                observations.add(actual);
                return satisfied(observations.size());
            };
        });
        var execution = bind(condition);

        assertEquals(0, factories[0]);
        var firstResult = execution.probe("first");
        var secondResult = execution.probe("second");
        var repeatedResult = execution.probe("first");

        assertEquals(1, result(firstResult));
        assertEquals(2, result(secondResult));
        assertEquals(3, result(repeatedResult));
        assertEquals(List.of("first", "second", "first"), observations);
        assertEquals(1, factories[0]);
    }

    @Test
    void thrownAndInvalidResultsBelongToTheirOwnCalls() throws Throwable {
        var failure = new AssertionError("first call failed");
        int[] calls = {0};
        Condition<String, Integer> condition = checkPerWait("three calls",
                () -> actual -> switch (++calls[0]) {
                    case 1 -> throw failure;
                    case 2 -> null;
                    default -> satisfied(actual.length());
                });
        var execution = bind(condition);

        assertSame(failure, assertThrows(Throwable.class, () -> execution.probe("ready")));
        var invalid = assertThrows(NullPointerException.class, () -> execution.probe("ready"));
        assertEquals("condition returned null AttemptResult", invalid.getMessage());
        assertEquals(5, result(execution.probe("ready")));
    }

    @Test
    void nullIsAValidObservation() throws Throwable {
        Condition<String, String> condition = checkPerWait("preserve null",
                () -> AttemptResult::satisfied);
        assertNull(result(bind(condition).probe(null)));
    }

    @Test
    void interruptedCallDoesNotInitializeTheConditionAndCanBeRetriedAfterClearingTheFlag() throws Throwable {
        int[] factories = {0};
        Condition<String, String> condition = checkPerWait("preserve", () -> {
            factories[0]++;
            return AttemptResult::satisfied;
        });
        var execution = bind(condition);

        currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> execution.probe("ready"));
        assertEquals(0, factories[0]);
        interrupted();

        assertEquals("ready", result(execution.probe("ready")));
        assertEquals(1, factories[0]);
    }

    @Test
    void factoryInterruptionSkipsEvaluationAndKeepsTheEvaluatorForTheNextProbe() throws Throwable {
        int[] factories = {0};
        int[] probes = {0};
        var execution = new GuardedAttempt<String, String>(() -> {
            factories[0]++;
            currentThread().interrupt();
            return actual -> { probes[0]++; return satisfied(actual); };
        });

        assertThrows(InterruptedException.class, () -> execution.probe("ready"));
        assertEquals(0, probes[0]);
        interrupted();
        assertEquals("ready", result(execution.probe("ready")));
        assertEquals(1, factories[0]);
        assertEquals(1, probes[0]);
    }

    @Test
    void ordinaryConditionKeepsMetadataAndCreatesIndependentEvaluators() throws Throwable {
        Condition<Integer, Integer> ordinary = checkPerWait("counted", () -> {
            int[] calls = {0};
            return ignored -> satisfied(++calls[0]);
        });
        assertEquals("counted", ordinary.description());
        var first = bind(ordinary);
        var second = bind(ordinary);

        assertEquals(1, result(first.probe(0)));
        assertEquals(2, result(first.probe(0)));
        assertEquals(1, result(second.probe(0)));
    }
}
