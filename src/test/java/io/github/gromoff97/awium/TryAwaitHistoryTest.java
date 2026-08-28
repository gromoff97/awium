package io.github.gromoff97.awium;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.ACQUISITION;
import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.conditioning.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditioning.conditions.Conditions.condition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TryAwaitHistoryTest {

    @Test
    void storesOnlyTheFirstOfAdjacentEquivalentAttempts() {
        var time = new FakeTime(0);
        var actual = new Object();
        var firstResult = new Object();
        var finalResult = new Object();
        int[] calls = {0};

        var execution = new WaitEngine(config(1, 10, 3), time, time).recordedWaitFor(
                () -> actual,
                value -> satisfied(calls[0]++ < 3 ? firstResult : finalResult));

        assertEquals(4, execution.outcome().attempt().number());
        assertEquals(List.of(1L, 2L, 4L), execution.attempts().stream()
                .map(AwaitAttempt::number).toList());
        assertEquals(List.of(ACQUISITION, PERSISTENCE, PERSISTENCE),
                execution.attempts().stream().map(AwaitAttempt::phase).toList());
        assertThrows(UnsupportedOperationException.class, execution.attempts()::clear);
    }

    @Test
    void compressionNeverCallsUserObjectMethods() {
        var time = new FakeTime(0);
        var probe = new ThrowingProbe();

        var execution = new WaitEngine(config(1, 10, 2), time, time)
                .recordedWaitFor(() -> probe, actual -> satisfied(probe));

        assertEquals(2, execution.attempts().size());
        assertEquals(3, execution.outcome().attempt().number());
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

        assertEquals(List.of(1L), numbers(same));
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
                    return Evaluation.<Object>unsatisfied("same mismatch").withContext(
                            new Evaluation.Context.Sequence(current - 1, 3, current,
                                    "stage " + current, null));
                });

        assertEquals(List.of(1L, 2L, 3L), numbers(execution));
        assertEquals(List.of(1, 2, 3), execution.attempts().stream()
                .map(AwaitAttempt::outcome)
                .map(outcome -> ((AwaitAttempt.Outcome.Unsatisfied<?, ?>) outcome)
                        .context())
                .map(Evaluation.Context.Sequence.class::cast)
                .map(Evaluation.Context.Sequence::evaluatedStageNumber)
                .toList());
    }

    @Test
    void capturedStageTransitionsAreNotCompressedWhenDiagnosticsMatch() {
        var time = new FakeTime(0);
        var actual = new Object();
        var nested = captured(
                condition("inner stage 1", value -> satisfied(value)),
                condition("inner stage 2", value -> satisfied(value)));

        var result = timedTryAwait(() -> actual, config(1, 10, 0), time, time)
                .until(captured(
                        condition("conditions are satisfied in order",
                                value -> satisfied(List.of(value))),
                        nested));

        assertEquals(3, result.totalAttempts());
        assertEquals(List.of(1L, 2L, 3L), result.attempts().stream()
                .map(AwaitAttempt::number).toList());
    }

    @Test
    void diagnosticRecordsHaveNoOptionalComponents() {
        Stream.of(AwaitResult.class, AwaitAttempt.class)
                .flatMap(TryAwaitHistoryTest::typeAndNestedTypes)
                .filter(Class::isRecord)
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .forEach(component -> assertNotEquals(Optional.class, component.getType(),
                        component.getDeclaringRecord().getName() + "." + component.getName()));
    }

    private static WaitEngine.RecordedWait<Object, Object> recordUnsatisfied(
            io.github.gromoff97.awium.sources.Source<Object> source,
            java.util.function.IntFunction<io.github.gromoff97.awium.conditioning.Evaluation<Object>> evaluation) {
        var time = new FakeTime(0);
        int[] calls = {0};
        return new WaitEngine(config(1, 2, 0), time, time)
                .recordedWaitFor(source, actual -> evaluation.apply(calls[0]++));
    }

    private static List<Long> numbers(WaitEngine.RecordedWait<?, ?> execution) {
        return execution.attempts().stream().map(AwaitAttempt::number).toList();
    }

    private static Stream<Class<?>> typeAndNestedTypes(Class<?> type) {
        return Stream.concat(Stream.of(type), Arrays.stream(type.getDeclaredClasses())
                .flatMap(TryAwaitHistoryTest::typeAndNestedTypes));
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
