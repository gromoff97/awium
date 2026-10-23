package io.github.gromoff97.awium;

import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AttemptRecord.Phase.ACQUISITION;
import static io.github.gromoff97.awium.AttemptRecord.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TryUntilHistoryTest {

    @Test
    void keepsEveryAttemptUntilTheLimit() {
        var time = new FakeTime(0);
        var firstResult = new Object();
        var finalResult = new Object();
        int[] calls = {0};

        var execution = timedAwait(() -> calls[0]++ < 3 ? firstResult : finalResult,
                config(1, 10, 3), time, time).tryUntil(isNotNull);

        assertEquals(4, execution.totalAttempts());
        assertEquals(List.of(1L, 2L, 3L, 4L), execution.attempts().stream()
                .map(AttemptRecord::number).toList());
        assertEquals(List.of(ACQUISITION, PERSISTENCE, PERSISTENCE, PERSISTENCE),
                execution.attempts().stream().map(AttemptRecord::phase).toList());
        assertThrows(UnsupportedOperationException.class, execution.attempts()::clear);
    }

    @Test
    void boundsHistoryWhileRetainingTheFirstAndLatestAttempts() {
        for (int count : new int[]{255, 256, 257, 300}) {
            var time = new FakeTime(0);
            var actual = new Object();
            var result = timedAwait(() -> actual, config(1, count, 0), time, time).tryUntil(
                    condition("never ready", value -> unsatisfied("not ready")));

            assertEquals(count, result.totalAttempts());
            assertEquals(Math.min(256, count), result.attempts().size());
            assertEquals(1, result.attempts().getFirst().number());
            assertEquals(Math.max(2, count - 254), result.attempts().get(1).number());
            assertEquals(count, result.attempts().getLast().number());
            assertThrows(UnsupportedOperationException.class, result.attempts()::clear);
        }
    }

    @Test
    void recordingNeverCallsUserObjectMethods() {
        var time = new FakeTime(0);
        var probe = new ThrowingProbe();

        var execution = timedAwait(() -> probe, config(1, 10, 2), time, time).tryUntil(condition("ready", actual -> satisfied(probe)));

        assertEquals(3, execution.attempts().size());
        assertEquals(List.of(1L, 2L, 3L), numbers(execution));
        assertEquals(3, execution.totalAttempts());
    }

    @Test
    void capturesEachStageEvenWhenDiagnosticsMatch() {
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
