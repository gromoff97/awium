package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.ConditionFactories.captured;

import java.util.List;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AwaitAttempt.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.ConditionResult.assertionUnsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;

import static io.github.gromoff97.awium.Conditions.condition;
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
                var reference = new ConditionResult.Reference<>(new String("Expected"), ++calls[0] <= 3 ? first : second);
                ConditionResult.Context context = sequence
                        ? new ConditionResult.Context.Sequence(0, 2, 1, new String("stage"), null, reference)
                        : new ConditionResult.Context.Expectation(new String("stage"), reference);
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

        var execution = timedAwait(() -> actual, config(1, 10, 3), time, time).tryUntil(condition("ready",
                value -> satisfied(calls[0]++ < 3 ? firstResult : finalResult)));

        assertEquals(4, execution.totalAttempts());
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

        var execution = timedAwait(() -> probe, config(1, 10, 2), time, time).tryUntil(condition("ready", actual -> satisfied(probe)));

        assertEquals(2, execution.attempts().size());
        assertEquals(List.of(1L, 3L), numbers(execution));
        assertEquals(3, execution.totalAttempts());
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
            return call == 5 ? satisfied(value) : ConditionResult.<Object>unsatisfied("not ready").withContext(
                    new ConditionResult.Context.Sequence(0, 2, 1, new String("stage"), new String("reason"),
                            new ConditionResult.Reference<>(new String("Expected"), call <= 2 ? first : second)));
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

        var execution = timedAwait(() -> actual, config(1, 3, 0), time, time).tryUntil(condition("sequence", value -> {
            int current = ++stage[0];
            return ConditionResult.<Object>unsatisfied("same mismatch").withContext(
                    new ConditionResult.Context.Sequence(current - 1, 3, current,
                            "stage " + current, null, null));
        }));

        assertEquals(List.of(1L, 2L, 3L), numbers(execution));
        assertEquals(List.of(1, 2, 3), execution.attempts().stream()
                .map(AwaitAttempt::outcome)
                .map(outcome -> ((AwaitAttempt.Outcome.Evaluated<?, ?>) outcome)
                        .evaluation().context())
                .map(ConditionResult.Context.Sequence.class::cast)
                .map(ConditionResult.Context.Sequence::evaluatedStageNumber)
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

    private static AwaitResult<Object, Object> recordUnsatisfied(
            io.github.gromoff97.awium.Source<Object> source,
            java.util.function.IntFunction<io.github.gromoff97.awium.ConditionResult<Object>> evaluation) {
        var time = new FakeTime(0);
        int[] calls = {0};
        return timedAwait(source, config(1, 2, 0), time, time).tryUntil(condition("not ready", actual -> evaluation.apply(calls[0]++)));
    }

    private static List<Long> numbers(AwaitResult<?, ?> execution) {
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
