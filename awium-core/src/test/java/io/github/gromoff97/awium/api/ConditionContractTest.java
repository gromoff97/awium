package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.Diagnostic;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionContractTest {
    @Test
    void referencesGuardUserRendering() {
        var value = new Object() {
            @Override public String toString() { throw new IllegalStateException("cannot print"); }
        };
        assertEquals("Expected=<unprintable>", new Reference<>("Expected", value).toString());
    }

    @Test
    void referenceLabelsCannotBeNull() {
        assertThrows(NullPointerException.class, () -> new Reference<>(null, "ready"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void referenceLabelsCannotBeBlank(String label) {
        assertThrows(IllegalArgumentException.class, () -> new Reference<>(label, "ready"));
    }

    @Test
    void aReasonCannotBeNull() {
        assertThrows(NullPointerException.class, () -> check("value", AttemptResult::satisfied).because(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void aReasonCannotBeBlank(String reason) {
        assertThrows(IllegalArgumentException.class, () -> check("value", AttemptResult::satisfied).because(reason));
    }

    @Test
    void replacingTheReasonManyTimesStillAllowsEvaluation() throws Throwable {
        Condition<String, Integer> condition = check("length",
                actual -> satisfied(actual.length()));
        for (int i = 0; i < 50_000; i++) condition = condition.because("updated reason");

        assertEquals(satisfied(5), condition.probe("ready"));
    }

    @Test
    void reasonsPreserveDirectProbeResults() throws Throwable {
        var expected = unsatisfied("not ready", new IllegalStateException("offline"));
        var condition = new Condition<String, Object>() {
            @Override public String description() { return "ready"; }
            @Override public AttemptResult<Object> probe(String actual) { return expected; }
        }.because("a value is required");

        assertSame(expected, condition.probe("pending"));
    }

    @Test
    void reasonsPreserveTheSuppliedEvaluator() throws Exception {
        AwaitAttempt<String, String> evaluator = actual -> satisfied(actual);
        var condition = new Condition<String, String>() {
            @Override public String description() { return "ready"; }
            @Override public AttemptResult<String> probe(String actual) { throw new AssertionError("use evaluator"); }
            @Override public AwaitAttempt<String, String> newAttempt() { return evaluator; }
        }.because("a value is required");

        assertSame(evaluator, condition.newAttempt());
    }

    @Test
    void reasonsLeaveTheOriginalUserConditionUnchanged() {
        var original = new Condition<String, Integer>() {
            @Override public String description() { return "text length"; }
            @Override public AttemptResult<Integer> probe(String actual) { return satisfied(actual.length()); }
        };
        var first = original.because("first");
        var second = first.because("second");

        assertNull(original.explanation());
        assertEquals("first", first.explanation());
        assertEquals("second", second.explanation());
        assertEquals(5, timedAwait(() -> "ready", new FakeTime(0)).until(second));
    }

    @Test
    void aConditionCanCompleteWhileRecordingAnUnsatisfiedCheck() {
        var condition = new Condition<String, Integer>() {
            @Override public String description() { return "measure text"; }
            @Override public AttemptResult<Integer> probe(String actual) { return satisfied(actual.length()); }
            @Override public AttemptResult<?> historyResult(AttemptResult<? extends Integer> attempt) { return unsatisfied("observed"); }
        }.because("use the completed measurement");
        var waiting = timedAwait(() -> "ready", new FakeTime(0));

        assertEquals(5, waiting.until(condition));
        var result = diagnostic(waiting).until(condition);
        assertEquals(5, result.orThrow());
        assertSame(condition, result.conditions().getFirst().condition());
        assertEquals("observed", assertInstanceOf(UnsatisfiedObservation.class, attempts(result).getFirst()).mismatch());
    }

    @Test
    void aRunReturnedWithAReasonKeepsItsHistoryAndDiagnostics() {
        var unused = check("unused", AttemptResult::satisfied);
        var observed = check("observed", AttemptResult::satisfied);
        var run = new Condition<String, String>() {
            @Override public String description() { return "check text"; }
            @Override public AttemptResult<String> probe(String actual) { return unsatisfied("pending"); }
            @Override public AttemptResult<?> historyResult(AttemptResult<? extends String> attempt) { return unsatisfied("recorded"); }
            @Override public int historySlot(Attempt<?> attempt) { return 2; }
            @Override public List<? extends Condition<?, ?>> historyConditions(Condition<?, ?> definition) { return List.of(unused, observed); }
            @Override public Diagnostic diagnostics(Attempt<?> attempt) { return new Diagnostic(null, new Reference<>("Run detail", "retained")); }
        }.because("check the prepared run");
        var definition = new Condition<String, String>() {
            @Override public String description() { return "prepare text check"; }
            @Override public AttemptResult<String> probe(String actual) { throw new AssertionError("use the run"); }
            @Override public Condition<String, String> newRun() { return run; }
        };

        var result = diagnostic(timedAwait(() -> "actual", new FakeTime(0))).upTo(ofNanos(1)).until(definition);

        assertEquals(List.of(unused, observed), result.conditions().stream().map(history -> history.condition()).toList());
        assertThat(result.conditions().getFirst().attempts()).isEmpty();
        assertEquals("recorded", assertInstanceOf(UnsatisfiedObservation.class,
                result.conditions().getLast().attempts().lastEntry().getValue()).mismatch());
        assertThat(assertInstanceOf(Failed.class, result).failure().message()).contains("Run detail: \"retained\"");
    }

    @Test
    void reasonsPreserveLazyEvaluatorsAndFreshStateForEachWait() {
        int[] factories = {0};
        var condition = new Condition<String, String>() {
            @Override public String description() { return "second observation"; }
            @Override public AttemptResult<String> probe(String actual) { throw new AssertionError("factory must supply the evaluator"); }
            @Override public AwaitAttempt<String, String> newAttempt() {
                factories[0]++;
                int[] calls = {0};
                return actual -> ++calls[0] == 2 ? satisfied(actual) : unsatisfied("waiting for a second observation");
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
