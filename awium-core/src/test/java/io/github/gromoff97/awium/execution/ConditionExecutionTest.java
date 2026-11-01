package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.support.FakeTime;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.AwaitAssertions.result;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionExecutionTest {

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
        Condition<String, Integer> condition = new DefinedCondition<>("count calls", null, () -> {
            factories[0]++;
            return actual -> {
                observations.add(actual);
                return ConditionResult.satisfied(observations.size());
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
    void thrownAndInvalidResultsBelongToTheirOwnCalls() {
        var failure = new AssertionError("first call failed");
        int[] calls = {0};
        Condition<String, Integer> condition = new DefinedCondition<>("three calls", null,
                () -> actual -> switch (++calls[0]) {
                    case 1 -> throw failure;
                    case 2 -> null;
                    default -> ConditionResult.satisfied(actual.length());
                });
        var execution = bind(condition);

        assertSame(failure, assertThrows(Throwable.class, () -> execution.probe("ready")));
        var invalid = assertThrows(NullPointerException.class, () -> execution.probe("ready"));
        assertEquals("condition returned null ConditionResult", invalid.getMessage());
        assertEquals(5, result(assertDoesNotThrow(() -> execution.probe("ready"))));
    }

    @Test
    void nullIsAValidObservation() throws Throwable {
        Condition<String, String> condition = new DefinedCondition<>("preserve null", null,
                () -> ConditionResult::satisfied);
        assertNull(result(bind(condition).probe(null)));
    }

    @Test
    void interruptedCallDoesNotInitializeTheConditionAndCanBeRetriedAfterClearingTheFlag() throws Throwable {
        int[] factories = {0};
        Condition<String, String> condition = new DefinedCondition<>("preserve", null, () -> {
            factories[0]++;
            return ConditionResult::satisfied;
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
    void ordinaryConditionKeepsMetadataAndCreatesIndependentEvaluators() {
        Condition<Integer, Integer> ordinary = new DefinedCondition<>(
                "counted", null, () -> {
                    int[] calls = {0};
                    return ignored -> ConditionResult.satisfied(++calls[0]);
                });
        assertEquals("counted", ordinary.description());
        var first = bind(ordinary);
        var second = bind(ordinary);

        assertEquals(1, result(assertDoesNotThrow(() -> first.probe(0))));
        assertEquals(2, result(assertDoesNotThrow(() -> first.probe(0))));
        assertEquals(1, result(assertDoesNotThrow(() -> second.probe(0))));
    }
    private final FakeTime time = new FakeTime(0);

    @Test
    void brokenUserMetadataDoesNotHideTheProbeFailure() {
        var probeFailure = new AssertionError("probe failed");
        var renderingFailure = new IllegalStateException("description failed");
        var condition = new Condition<String, String>() {
            @Override public String description() { throw renderingFailure; }
            @Override public ConditionResult<String> probe(String actual) { throw probeFailure; }
        };
        var waiting = timedAwait(() -> "ready", time);
        var result = diagnostic(waiting).until(condition);
        assertSame(probeFailure, failure(result).cause());
        assertThat(failure(result).suppressed()).containsExactly(renderingFailure);
        assertThat(result.toString()).contains("Condition: <unprintable>", "probe failed");
    }
}
