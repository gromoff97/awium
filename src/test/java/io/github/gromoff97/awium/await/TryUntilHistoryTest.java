package io.github.gromoff97.awium.await;

import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.captured;

import io.github.gromoff97.awium.FakeTime;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.internal.engine.WaitEngine;
import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;

import static io.github.gromoff97.awium.conditions.Conditions.condition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TryUntilHistoryTest {

    @Test
    void successfulHistoryKeepsContextChangesWithoutCallingUserEquality() {
        for (boolean sequence : new boolean[]{true, false}) {
            var time = new FakeTime(0);
            var actual = new Object();
            var first = new ThrowingProbe();
            var second = new ThrowingProbe();
            int[] calls = {0};
            var result = timedAwait(() -> actual, config(1, 10, 4), time, time).tryUntil(condition("ready", value -> {
                var reference = new AwaitAttempt.Reference<>(new String("Expected"), ++calls[0] <= 3 ? first : second);
                AwaitAttempt.Context context = sequence
                        ? new AwaitAttempt.Context.Sequence(0, 2, 1, new String("stage"), null, reference)
                        : new AwaitAttempt.Context.Expectation(new String("stage"), reference);
                return satisfied(value).withContext(context);
            }));
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
            assertEquals(List.of(1L, 3L, 5L), result.attempts().stream().map(AwaitAttempt::number).toList());
        }
    }

    @Test
    void storesOnlyTheLastOfAdjacentEquivalentAttempts() {
        var time = new FakeTime(0);
        var actual = new Object();
        var firstResult = new Object();
        var finalResult = new Object();
        int[] calls = {0};

        var execution = new WaitEngine(config(1, 10, 3), time, time).recordedWaitFor(
                () -> actual,
                value -> satisfied(calls[0]++ < 3 ? firstResult : finalResult));

        assertEquals(4, execution.outcome().attempt().number());
        assertEquals(List.of(1L, 3L, 4L), execution.attempts().stream()
                .map(AwaitAttempt::number).toList());
        assertEquals(List.of(ACQUISITION, PERSISTENCE, PERSISTENCE),
                execution.attempts().stream().map(AwaitAttempt::phase).toList());
        assertThrows(UnsupportedOperationException.class, execution.attempts()::clear);
    }

    @Test
    void boundsHistoryWhileRetainingTheFirstAndLatestAttempts() {
        var time = new FakeTime(0);

        var result = timedAwait(Object::new, config(1, 300, 0), time, time)
                .tryUntil(condition("never ready", actual -> unsatisfied("not ready")));

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

        var execution = new WaitEngine(config(1, 10, 2), time, time)
                .recordedWaitFor(() -> probe, actual -> satisfied(probe));

        assertEquals(2, execution.attempts().size());
        assertEquals(List.of(1L, 3L), numbers(execution));
        assertEquals(3, execution.outcome().attempt().number());
    }

    @Test
    void compressionComparesContextValuesByIdentityWithoutCallingUserMethods() {
        var time = new FakeTime(0);
        var actual = new Object();
        var first = new ThrowingProbe();
        var second = new ThrowingProbe();
        int[] calls = {0};

        var result = timedAwait(() -> actual, config(1, 10, 0), time, time).tryUntil(condition("ready", value -> {
            int call = ++calls[0];
            return call == 5 ? satisfied(value) : ConditionEvaluation.<Object>unsatisfied("not ready").withContext(
                    new AwaitAttempt.Context.Sequence(0, 2, 1, new String("stage"), new String("reason"),
                            new AwaitAttempt.Reference<>(new String("Expected"), call <= 2 ? first : second)));
        }));

        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(5, result.totalAttempts());
        assertEquals(List.of(2L, 4L, 5L), result.attempts().stream().map(AwaitAttempt::number).toList());
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
        var time = new FakeTime(0);
        var actual = new Object();
        int[] stage = {0};

        var execution = new WaitEngine(config(1, 3, 0), time, time)
                .recordedWaitFor(() -> actual, value -> {
                    int current = ++stage[0];
                    return ConditionEvaluation.<Object>unsatisfied("same mismatch").withContext(
                            new AwaitAttempt.Context.Sequence(current - 1, 3, current,
                                    "stage " + current, null, null));
                });

        assertEquals(List.of(1L, 2L, 3L), numbers(execution));
        assertEquals(List.of(1, 2, 3), execution.attempts().stream()
                .map(AwaitAttempt::outcome)
                .map(outcome -> ((AwaitAttempt.Outcome.Unsatisfied<?, ?>) outcome)
                        .context())
                .map(AwaitAttempt.Context.Sequence.class::cast)
                .map(AwaitAttempt.Context.Sequence::evaluatedStageNumber)
                .toList());
    }

    @Test
    void capturedStageTransitionsAreNotCompressedWhenDiagnosticsMatch() {
        var time = new FakeTime(0);
        var actual = new Object();
        var nested = captured(
                condition("inner stage 1", value -> satisfied(value)),
                condition("inner stage 2", value -> satisfied(value)));

        var result = timedAwait(() -> actual, config(1, 10, 0), time, time).tryUntil(condition("conditions are satisfied in order",
                                value -> satisfied(List.of(value))),
                        nested);

        assertEquals(3, result.totalAttempts());
        assertEquals(List.of(1L, 2L, 3L), result.attempts().stream()
                .map(AwaitAttempt::number).toList());
    }

    private static WaitEngine.RecordedWait<Object, Object> recordUnsatisfied(
            io.github.gromoff97.awium.sources.Source<Object> source,
            java.util.function.IntFunction<io.github.gromoff97.awium.condition.ConditionEvaluation<Object>> evaluation) {
        var time = new FakeTime(0);
        int[] calls = {0};
        return new WaitEngine(config(1, 2, 0), time, time)
                .recordedWaitFor(source, actual -> evaluation.apply(calls[0]++));
    }

    private static List<Long> numbers(WaitEngine.RecordedWait<?, ?> execution) {
        return execution.attempts().stream().map(AwaitAttempt::number).toList();
    }

    private static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
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
