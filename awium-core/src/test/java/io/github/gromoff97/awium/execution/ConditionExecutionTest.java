package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.support.TestCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.support.AwaitAssertions.result;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.AwaitCondition.Reference;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ConditionExecutionTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "waiting for another observation"})
    void anIncompleteRunRetainsItsFieldsAndAlwaysExplainsTheMismatch(String message) throws Throwable {
        Condition<String, String> condition = mock();
        ConditionRun<String, String> run = mock();
        var diagnostic = new Diagnostic(message, new Reference<>("Completed checks", 1));
        doReturn(run).when(condition).newRun(condition);
        doReturn(satisfied("selected", diagnostic)).when(run).probe("actual");

        var rejected = assertInstanceOf(Unsatisfied.class, bind(condition).probe("actual"));

        assertEquals(message == null || message.isBlank() ? "condition is not yet complete" : message,
                rejected.mismatch());
        assertEquals(diagnostic.fields(), rejected.diagnostic().fields());
    }

    @Test
    void evaluatorFactoriesAcceptBroaderInputsAndNarrowerResults() throws Throwable {
        Callable<AwaitAttempt<CharSequence, Integer>> factory = () -> actual -> satisfied(actual.length());
        var execution = new ConditionExecution<String, Number>(factory);

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
        Condition<String, Integer> condition = new TestCondition<>("count calls", null, () -> {
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
        Condition<String, Integer> condition = new TestCondition<>("three calls", null,
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
        Condition<String, String> condition = new TestCondition<>("preserve null", null,
                () -> AttemptResult::satisfied);
        assertNull(result(bind(condition).probe(null)));
    }

    @Test
    void interruptedCallDoesNotInitializeTheConditionAndCanBeRetriedAfterClearingTheFlag() throws Throwable {
        int[] factories = {0};
        Condition<String, String> condition = new TestCondition<>("preserve", null, () -> {
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
        var execution = new ConditionExecution<String, String>(() -> {
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
        Condition<Integer, Integer> ordinary = new TestCondition<>(
                "counted", null, () -> {
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
