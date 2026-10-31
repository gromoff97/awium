package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;

class ConditionHistoriesTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void ordinarySelectionHasOneHistoryOfTheCompleteSourceType() {
        var source = List.of(Optional.<String>empty(), Optional.of("ready")).iterator();
        var result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1)).until(present);

        String selected = result.orThrow();
        assertEquals("ready", selected);
        assertThat(result.conditions()).hasSize(1);
        ConditionHistory<Optional<String>> history = result.conditions().getFirst();
        assertSame(present, history.condition());
        assertThat(history.attempts()).hasSize(2);
        Optional<String> observation = observation(history.attempts().lastEntry().getValue());
        assertEquals(Optional.of(selected), observation);
    }

    @Test
    void capturedKeepsEachConditionsTransitionsInItsOwnHistory() {
        var first = equalTo(10);
        var second = equalTo(20);
        var third = equalTo(30);
        var source = List.of(0, 0, 10, 10, 20, 20, 30).iterator();
        var result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1))
                .until(captured(first, second, third));

        assertEquals(List.of(10, 20, 30), result.orThrow());
        assertThat(result.conditions()).extracting(ConditionHistory::condition).containsExactly(first, second, third);
        assertThat(result.conditions()).extracting(history ->
                history.attempts().values().stream().map(AwaitTestAccess::observation).toList())
                .containsExactly(List.of(0, 10), List.of(10, 20), List.of(20, 30));
        assertThat(result.conditions()).extracting(history -> history.attempts().firstKey()).containsExactly(2L, 4L, 6L);
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(2L, 3L);
    }

    @Test
    void repeatedConditionInstancesHaveSeparateHistories() {
        var condition = equalTo("ready");
        var result = tryAwait(() -> "ready").usingTime(time, time).every(ofNanos(1))
                .until(captured(condition, condition, condition));

        assertEquals(List.of("ready", "ready", "ready"), result.orThrow());
        assertThat(result.conditions()).hasSize(3);
        assertNotSame(result.conditions().getFirst(), result.conditions().getLast());
        for (int i = 0; i < result.conditions().size(); i++) {
            var history = result.conditions().get(i);
            assertSame(condition, history.condition());
            assertThat(history.attempts()).hasSize(1);
            assertEquals(i + 1, history.attempts().firstKey());
        }
    }

    @Test
    void failureRetainsCompletedCurrentAndUnreachedConditionsWithoutInitializingTheLast() {
        int[] initialized = {0};
        var unreached = conditionPerWait("third", () -> {
            initialized[0]++;
            return (Integer actual) -> satisfied(actual);
        }).because("last check");
        var source = List.of(1, 0, 0, 2, 2).iterator();
        var result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(5))
                .until(captured(equalTo(1), equalTo(3), unreached));

        failure(AwaitFailure.Reason.TIMEOUT, result);
        assertThat(result.conditions()).hasSize(3);
        assertEquals(1, observation(result.conditions().getFirst().attempts().firstEntry().getValue()));
        assertThat(result.conditions().get(1).attempts().values()).extracting(AwaitTestAccess::observation)
                .containsExactly(0, 2);
        var last = result.conditions().getLast();
        assertSame(unreached, last.condition());
        assertThat(last.attempts()).isEmpty();
        assertEquals(0, initialized[0]);
        assertThat(last.toString()).contains("not attempted", "last check");
        assertThat(result.toString()).contains("Condition 3:", "not attempted");
    }

    @Test
    void sourceFailureBeforeTheFirstCheckStillRetainsAllConditionDefinitions() {
        var cause = new IllegalStateException("source unavailable");
        Callable<Integer> source = () -> { throw cause; };
        var first = equalTo(1);
        var second = equalTo(2);
        var result = tryAwait(source).until(captured(first, second));

        assertSame(cause, failure(result).cause());
        assertThat(result.conditions()).extracting(ConditionHistory::condition).containsExactly(first, second);
        var attempt = assertInstanceOf(Attempt.FailedBeforeObservation.class,
                result.conditions().getFirst().attempts().firstEntry().getValue());
        assertEquals(Attempt.Operation.SOURCE, attempt.operation());
        assertThat(result.conditions().getLast().attempts()).isEmpty();
    }

    @Test
    void persistenceRemainsInTheFinalConditionsHistory() {
        var source = List.of(1, 2, 2, 2).iterator();
        var result = tryAwait(source::next).usingTime(time, time).every(ofNanos(1)).persisting(ofNanos(2))
                .until(captured(equalTo(1), equalTo(2)));

        assertEquals(List.of(1, 2), result.orThrow());
        assertThat(result.conditions()).hasSize(2);
        assertThat(result.conditions().getFirst().attempts()).hasSize(1);
        var attempts = result.conditions().getLast().attempts();
        assertThat(attempts.keySet()).containsExactly(4L);
        assertEquals(2, observation(attempts.lastEntry().getValue()));
    }

    @Test
    void unreachedConditionRenderingGuardsItsOperands() {
        Object expected = new Object() {
            @Override public String toString() { throw new IllegalStateException("renderer failed"); }
        };
        var history = new ConditionHistory<>(equalTo(expected), new java.util.TreeMap<>());

        String report = history.toString();
        assertThat(report).contains("not attempted", "<unprintable>", "Rendering failure: java.lang.IllegalStateException");
    }
}
