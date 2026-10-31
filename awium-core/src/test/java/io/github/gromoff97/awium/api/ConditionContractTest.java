package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.AbstractConditionRun;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.support.FakeTime;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionContractTest {
    @Test
    void reasonsLeaveTheOriginalUserConditionUnchanged() {
        var original = new Condition<String, Integer>() {
            @Override public String description() { return "text length"; }
            @Override public ConditionResult<Integer> probe(String actual) { return satisfied(actual.length()); }
        };
        var first = original.because("first");
        var second = first.because("second");

        assertNull(original.explanation());
        assertEquals("first", first.explanation());
        assertEquals("second", second.explanation());
        assertEquals(5, timedAwait(() -> "ready", new FakeTime(0)).until(second));
    }

    @Test
    void aUserRunCanCompleteEvenWhenItsLastProbeWasUnsatisfied() {
        var condition = new Condition<String, Integer>() {
            @Override public String description() { return "measure text"; }
            @Override public ConditionResult<Integer> probe(String actual) { return unsatisfied("observed"); }
            @Override public ConditionRun<String, Integer> newRun(AwaitCondition definition, boolean recording) {
                return new AbstractConditionRun<>(definition, recording) {
                    private Integer length;
                    @Override public ConditionResult<?> probe(String actual) {
                        length = actual.length();
                        return unsatisfied("observed");
                    }
                    @Override public boolean complete() { return length != null; }
                    @Override public Integer result() { return length; }
                };
            }
        }.because("use the completed measurement");
        var waiting = timedAwait(() -> "ready", new FakeTime(0));

        assertEquals(5, waiting.until(condition));
        var result = diagnostic(waiting).until(condition);
        assertEquals(5, result.orThrow());
        assertSame(condition, result.conditions().getFirst().condition());
        assertEquals("observed", assertInstanceOf(UnsatisfiedObservation.class, attempts(result).getFirst()).mismatch());
    }

    @Test
    void reasonsPreserveLazyEvaluatorsAndFreshStateForEachWait() {
        int[] factories = {0};
        var condition = new Condition<String, String>() {
            @Override public String description() { return "second observation"; }
            @Override public ConditionResult<String> probe(String actual) { throw new AssertionError("factory must supply the evaluator"); }
            @Override public AwaitAttempt<String, String> newAttempt() {
                factories[0]++;
                int[] calls = {0};
                return actual -> ++calls[0] == 2 ? satisfied(actual) : unsatisfied();
            }
        }.because("fresh state");
        var waiting = timedAwait(() -> "ready", new FakeTime(0));

        assertEquals(0, factories[0]);
        assertEquals("ready", waiting.until(condition));
        assertEquals(1, factories[0]);
        var result = diagnostic(waiting).until(condition);
        assertEquals("ready", result.orThrow());
        assertEquals(2L, result.conditions().getFirst().attempts().lastKey());
        assertEquals(2, factories[0]);
    }
}
