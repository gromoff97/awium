package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.AwaitAssertions.observation;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static java.time.Duration.ofNanos;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TryAwaitHistoryTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void groupsRepeatedReferencesWithoutCallingTheirObjectMethods() {
        var probe = new ThrowingProbe();
        var execution = timedTryAwait(() -> probe,
                config(1, 10, 3), time, time).until(check("ready", actual -> satisfied(actual)));

        assertEquals(1, attempts(execution).size());
        assertEquals(4L, execution.conditions().getFirst().attempts().lastKey());
        assertSame(probe, observation(attempts(execution).getFirst()));
        assertEquals(3, time.getAsLong());
        assertThrows(UnsupportedOperationException.class, attempts(execution)::clear);
    }

    @ParameterizedTest(name = "count={0}")
    @ValueSource(ints = {255, 256, 257, 10_000})
    void repeatedObservationsAndDiagnosticsKeepTheLastNumberWithoutATailLimit(int count) {
        int[] calls = {0};
        var result = timedTryAwait(() -> {
            calls[0]++;
            return 1;
        }, config(1, count, 0), time, time).until(
                check("never ready", value -> unsatisfied("not ready")));

        assertEquals(count, calls[0]);
        assertEquals(1, attempts(result).size());
        var attempt = attempts(result).getFirst();
        assertEquals((long) count, result.conditions().getFirst().attempts().lastKey());
        assertEquals(1, observation(attempt));
        assertThat(failure(result).message()).contains("Attempt: " + count + " (ACQUISITION)");
        assertThrows(UnsupportedOperationException.class, attempts(result)::clear);
    }

    @Test
    void everyDiagnosticTransitionSurvivesBeyondTheFormerTailLimit() {
        int[] calls = {0};
        var result = withTime(tryAwait(() -> ++calls[0]), time, time)
                .every(ofNanos(1)).upTo(ofNanos(1_000))
                .until(check("changing", value -> unsatisfied("value " + value)));

        assertEquals(1_000, attempts(result).size());
        for (int i = 0; i < attempts(result).size(); i++) {
            var attempt = attempts(result).get(i);
            assertSame(attempt, result.conditions().getFirst().attempts().get((long) i + 1));
            assertEquals(i + 1, observation(attempt));
        }
    }

    @Test
    void successKeepsAllGroupsAndTheTerminalValue() {
        assertEquals(300, historyThrough300Attempts(300).orThrow());
    }

    @Test
    void timeoutKeepsAllGroupsAndTheTerminalFailure() {
        var result = historyThrough300Attempts(301);
        assertThrows(AwaitAssertionError.class, result::orThrow);
    }

    private AwaitResult<Integer, Integer> historyThrough300Attempts(int expected) {
        int[] calls = {0};
        var result = withTime(tryAwait(() -> ++calls[0])
                .every(ofNanos(1)).upTo(ofNanos(300)), time, time)
                .until(expected(expected));

        assertEquals(300, calls[0]);
        assertEquals(300, result.conditions().getFirst().attempts().lastKey());
        assertEquals(1, observation(attempts(result).getFirst()));
        assertEquals(300, observation(attempts(result).getLast()));
        assertEquals(300, attempts(result).size());
        return result;
    }

    @Test
    void immutableConfigurationsAndRepeatedWaitsOwnIndependentHistories() {
        var original = withTime(tryAwait(() -> "ready"), time, time)
                .every(ofNanos(1)).upTo(ofNanos(10)).persisting(ofNanos(3));
        var otherTime = new FakeTime(0);
        var changed = withTime(original.every(ofNanos(2)).upTo(ofNanos(20))
                .persisting(ofNanos(6)), otherTime, otherTime);

        changed.until(accepted);
        assertEquals(6, otherTime.getAsLong());
        assertEquals(0, time.getAsLong());
        var shorter = original.until(accepted);
        assertEquals(3, time.getAsLong());
        assertEquals(4L, original.until(accepted).conditions().getFirst().attempts().lastKey());
        assertEquals(4, shorter.conditions().getFirst().attempts().lastKey());
    }

    @Test
    void retainsEveryNewErrorWithoutReadingItsMessageOrComparingIt() {
        var errors = range(0, 3).mapToObj(ignored -> new AssertionError("same message") {
            @Override public String getMessage() { throw new AssertionError("message must not be read"); }
            @Override public boolean equals(Object other) { throw new AssertionError("equals must not be called"); }
            @Override public int hashCode() { throw new AssertionError("hashCode must not be called"); }
            @Override public String toString() { throw new AssertionError("toString must not be called"); }
        }).toList();
        var outcomes = concat(errors.stream().map(error -> unsatisfied("pending", error)), Stream.of(satisfied(1))).iterator();
        var result = withTime(tryAwait(() -> 1), time, time).every(ofNanos(1))
                .until(check("ready", value -> outcomes.next()));

        assertEquals(1, result.orThrow());
        assertEquals(4, attempts(result).size());
        for (int i = 0; i < errors.size(); i++) {
            assertSame(errors.get(i), attempts(result).get(i).cause());
            assertSame(attempts(result).get(i), result.conditions().getFirst().attempts().get((long) i + 1));
        }
        String report = result.toString();
        assertEquals(3, report.lines().filter(line -> line.strip().startsWith("Cause:")).count());
        assertEquals(1, report.lines().filter(line -> line.startsWith("Condition ")).count());
    }

    @Test
    void repetitionsOfTheSameErrorCanStillBeGrouped() {
        var error = new AssertionError("same error");
        var observations = List.of(1, 1, 1, 4).iterator();
        var outcomes = List.<ConditionResult<Integer>>of(unsatisfied("pending", error), unsatisfied("pending", error),
                unsatisfied("pending", error), satisfied(4)).iterator();
        var result = withTime(tryAwait(observations::next), time, time).every(ofNanos(1))
                .until(check("ready", value -> outcomes.next()));

        assertEquals(4, result.orThrow());
        assertEquals(2, attempts(result).size());
        assertEquals(3, result.conditions().getFirst().attempts().firstKey());
        assertSame(error, attempts(result).getFirst().cause());
    }

    @Test
    void failedObservationComparisonsKeepSeparateAttemptsWithoutFailingTheWait() {
        var result = timedTryAwait(ThrowingProbe::new,
                config(1, 10, 3), time, time).until(check("ready", actual -> satisfied(actual)));

        assertInstanceOf(Succeeded.class, result);
        assertThat(attempts(result)).hasSize(4).hasOnlyElementsOfType(Attempt.SatisfiedObservation.class);
        assertSame(observation(attempts(result).getLast()), result.orThrow());
    }

    private static final class ThrowingProbe {
        @Override public boolean equals(Object other) { throw new AssertionError("equals must not be called"); }
        @Override public int hashCode() { throw new AssertionError("hashCode must not be called"); }
        @Override public String toString() { throw new AssertionError("toString must not be called"); }
    }
}
