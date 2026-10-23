package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.AttemptRecord.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AttemptRecord.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.ConditionResult.assertionUnsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TryUntilHistoryTest {

    @Test
    void successfulHistoryKeepsExpectationChangesWithoutCallingUserEquality() {
        for (boolean sequence : new boolean[]{true, false}) {
            var history = new AttemptHistory<Object, Object>();
            var actual = new Object();
            var first = new ThrowingProbe();
            var second = new ThrowingProbe();
            var root = diagnosticCondition("ready", null, null);
            for (int number = 1; number <= 5; number++) {
                var expectation = diagnosticCondition(new String("stage"), null,
                        new Condition.Reference<>(new String("Expected"), number <= 3 ? first : second));
                history.accept(attempt(number, number == 1 ? ACQUISITION : PERSISTENCE, actual, satisfied(actual),
                        sequence ? root : expectation, sequence ? new AttemptRecord.Progress(2, 2, 2, expectation) : null));
            }
            assertEquals(List.of(1L, 3L, 5L), history.snapshot().stream().map(AttemptRecord::number).toList());
        }
    }

    @Test
    void storesOnlyTheLastOfAdjacentEquivalentAttempts() {
        var time = new FakeTime(0);
        var firstResult = new Object();
        var finalResult = new Object();
        int[] calls = {0};

        var execution = timedAwait(() -> calls[0]++ < 3 ? firstResult : finalResult,
                config(1, 10, 3), time, time).tryUntil(isNotNull);

        assertEquals(4, execution.totalAttempts());
        assertEquals(List.of(1L, 3L, 4L), execution.attempts().stream()
                .map(AttemptRecord::number).toList());
        assertEquals(List.of(ACQUISITION, PERSISTENCE, PERSISTENCE),
                execution.attempts().stream().map(AttemptRecord::phase).toList());
        assertThrows(UnsupportedOperationException.class, execution.attempts()::clear);
    }

    @Test
    void boundsHistoryWhileRetainingTheFirstAndLatestAttempts() {
        var time = new FakeTime(0);

        var result = timedAwait(Object::new, config(1, 300, 0), time, time).tryUntil(
                condition("never ready", actual -> unsatisfied("not ready")));

        assertEquals(300, result.totalAttempts());
        assertEquals(256, result.attempts().size());
        assertEquals(1, result.attempts().getFirst().number());
        assertEquals(46, result.attempts().get(1).number());
        assertEquals(300, result.attempts().getLast().number());
    }

    @Test
    void compressionNeverCallsUserObjectMethods() {
        var time = new FakeTime(0);
        var probe = new ThrowingProbe();

        var execution = timedAwait(() -> probe, config(1, 10, 2), time, time).tryUntil(condition("ready", actual -> satisfied(probe)));

        assertEquals(2, execution.attempts().size());
        assertEquals(List.of(1L, 3L), numbers(execution));
        assertEquals(3, execution.totalAttempts());
    }

    @Test
    void compressionComparesExpectedValuesByIdentityWithoutCallingUserMethods() {
        var history = new AttemptHistory<Object, Object>();
        var actual = new Object();
        var first = new ThrowingProbe();
        var second = new ThrowingProbe();
        var root = diagnosticCondition("ready", null, null);
        for (int number = 1; number <= 5; number++) {
            var stage = diagnosticCondition(new String("stage"), new String("reason"),
                    new Condition.Reference<>(new String("Expected"), number <= 2 ? first : second));
            history.accept(attempt(number, ACQUISITION, actual,
                    number == 5 ? satisfied(actual) : unsatisfied("not ready"),
                    root, new AttemptRecord.Progress(0, 2, 1, stage)));
        }
        assertEquals(List.of(2L, 4L, 5L), history.snapshot().stream().map(AttemptRecord::number).toList());
    }

    @Test
    void mismatchUsesValueWhileObservedAndAssertionUseIdentity() {
        var sameActual = new Object();
        var sameAssertion = new AssertionError();

        var same = recordUnsatisfied(() -> sameActual,
                call -> assertionUnsatisfied(new String("same"), sameAssertion));
        var changedActual = recordUnsatisfied(Object::new,
                call -> unsatisfied(new String("same")));
        var changedAssertion = recordUnsatisfied(() -> sameActual,
                call -> assertionUnsatisfied(new String("same"), new AssertionError()));

        assertEquals(List.of(2L), numbers(same));
        assertEquals(List.of(1L, 2L), numbers(changedActual));
        assertEquals(List.of(1L, 2L), numbers(changedAssertion));
    }

    @Test
    void sequenceStageChangesAreNotCompressed() {
        var history = new AttemptHistory<Object, Object>();
        var actual = new Object();
        var expectation = diagnosticCondition("same stage description", null, null);
        for (int stage = 1; stage <= 3; stage++) {
            history.accept(attempt(stage, ACQUISITION, actual, unsatisfied("same mismatch"),
                    expectation, new AttemptRecord.Progress(stage - 1, 3, stage, expectation)));
        }
        assertEquals(List.of(1L, 2L, 3L), history.snapshot().stream().map(AttemptRecord::number).toList());
        assertEquals(List.of(1, 2, 3), history.snapshot().stream()
                .map(AttemptRecord::outcome)
                .map(outcome -> ((AttemptRecord.Outcome.Evaluated<?, ?>) outcome).progress().evaluatedStageNumber())
                .toList());
    }

    @Test
    void capturedStageTransitionsAreNotCompressedWhenDiagnosticsMatch() {
        var time = new FakeTime(0);
        var actual = new Object();
        var result = timedAwait(() -> actual, config(1, 10, 0), time, time).tryUntil(
                condition("same description", ConditionResult::satisfied),
                condition("same description", ConditionResult::satisfied),
                condition("same description", ConditionResult::satisfied));

        assertEquals(3, result.totalAttempts());
        assertEquals(List.of(1L, 2L, 3L), result.attempts().stream()
                .map(AttemptRecord::number).toList());
    }

    private static AttemptRecord<Object, Object> attempt(int number, AttemptRecord.Phase phase, Object actual,
            ConditionResult<Object> result, Condition<?, ?> expectation, AttemptRecord.Progress progress) {
        var timing = new AttemptRecord.Timing.AfterObservation(
                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ofNanos(number));
        return new AttemptRecord<>(number, phase,
                new AttemptRecord.Outcome.Evaluated<>(timing, actual, result, expectation, progress));
    }

    private static AwaitResult<Object, Object> recordUnsatisfied(
            Source<Object> source, IntFunction<ConditionResult<Object>> evaluation) {
        var time = new FakeTime(0);
        int[] calls = {0};
        return timedAwait(source, config(1, 2, 0), time, time).tryUntil(condition("not ready", actual -> evaluation.apply(calls[0]++)));
    }

    private static List<Long> numbers(AwaitResult<?, ?> execution) {
        return execution.attempts().stream().map(AttemptRecord::number).toList();
    }

    private static final class ThrowingProbe {

        @Override
        public boolean equals(Object other) {
            throw new AssertionError("equals must not be called");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("hashCode must not be called");
        }

        @Override
        public String toString() {
            throw new AssertionError("toString must not be called");
        }
    }
}
