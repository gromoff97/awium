package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.cartesian.CartesianTest;
import org.junitpioneer.jupiter.cartesian.CartesianTest.Values;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimingConfigurationTest {

    private final FakeTime time = new FakeTime(0);

    @CartesianTest(name = "origin={0}, persistence={1}, outcome={2}")
    void persistenceKeepsPositiveElapsedTimeBeyondSignedNanoseconds(
            @Values(longs = {0, Long.MAX_VALUE - 2, Long.MIN_VALUE + 2}) long origin,
            @Values(longs = {2, Long.MAX_VALUE}) long persistence,
            @Values(strings = {"success", "mismatch", "condition failure", "source failure"}) String outcome) {
        var time = new FakeTime(origin);
        var cause = new IllegalStateException(outcome);
        int[] calls = {0};
        var waiting = await(() -> {
            if (++calls[0] == 1) time.advanceNanos(Long.MAX_VALUE - 1);
            else if (outcome.equals("source failure")) throw cause;
            return "ready";
        }).usingTime(time, time).every(ofNanos(Long.MAX_VALUE))
                .upTo(ofNanos(Long.MAX_VALUE)).persisting(ofNanos(persistence));
        var result = diagnostic(waiting).until(Conditions.condition("ready", (String actual) -> {
            if (calls[0] > 1) {
                if (outcome.equals("condition failure")) throw cause;
                if (outcome.equals("mismatch")) return ConditionResult.unsatisfied("changed");
            }
            return ConditionResult.satisfied();
        }));

        assertEquals(2, calls[0], outcome);
        assertEquals(outcome.equals("success") ? 1 : 2, attempts(result).size(), outcome);
        assertEquals(2L, result.conditions().getFirst().attempts().lastKey());
        assertEquals(switch (outcome) {
            case "source failure" -> Attempt.FailedBeforeObservation.class;
            case "condition failure" -> Attempt.FailedAfterObservation.class;
            case "mismatch" -> Attempt.Unsatisfied.class;
            default -> Attempt.Satisfied.class;
        }, attempts(result).getLast().getClass());
        assertEquals(origin + Long.MAX_VALUE - 1 + persistence, time.getAsLong());
        if (outcome.equals("success")) {
            assertEquals("ready", result.orThrow());
        } else {
            var failure = failure(result);
            assertEquals(switch (outcome) {
                case "mismatch" -> PERSISTENCE_FAILED;
                case "condition failure" -> CONDITION_FAILED;
                default -> SOURCE_FAILED;
            }, failure.reason());
            assertSame(outcome.equals("mismatch") ? null : cause, failure.cause());
            assertThat(failure.message()).contains("elapsed " + (persistence == 2 ? "106751 d" : "213503 d"));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 100})
    void timeoutAtMostTheDefaultIntervalAllowsImmediateSuccessAndOneFailedObservation(long timeoutMillis) {
        var timeout = ofMillis(timeoutMillis);
        assertEquals("ready", await(() -> "ready").usingTime(time, time).upTo(timeout).until(isNotNull));
        assertThat(time.parkRequests).isEmpty();

        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time).upTo(timeout).until(equalTo(2));

        var failure = failure(result);
        assertEquals(TIMEOUT, failure.reason());
        assertEquals(1, calls[0]);
        assertEquals(1, attempts(result).size());
        assertEquals(timeout.toNanos(), time.getAsLong());
        assertThat(time.parkRequests).containsExactly(timeout.toNanos());
    }

    @Test
    void diagnosticMapPersistenceRechecksTheSelectedValue() {
        var states = List.of(Map.of("key", "ready"), Map.of("key", "changed")).iterator();
        var result = tryAwait(states::next).usingTime(time, time).every(ofNanos(1))
                .persisting(ofNanos(2)).until(MapConditions.valueFor("key", equalTo("ready")));

        var failure = failure(result);
        assertEquals(PERSISTENCE_FAILED, failure.reason());
        assertEquals(2, attempts(result).size());
        assertEquals(1, time.getAsLong());
    }

    @Test
    void timeReplacementPreservesConfigurationAndLeavesTheOriginalIndependent() {
        var originalTime = new FakeTime(0);
        var replacementTime = new FakeTime(0);
        var original = await(() -> "ready").every(ofNanos(1)).upTo(ofNanos(10))
                .usingTime(originalTime, originalTime).persisting(ofNanos(2));
        var replacement = original.usingTime(replacementTime, replacementTime).upTo(ofNanos(20));

        assertEquals("ready", replacement.until(isNotNull));
        assertEquals(2, replacementTime.getAsLong());
        assertEquals(0, originalTime.getAsLong());
        assertEquals("ready", original.until(isNotNull));
        assertEquals(2, originalTime.getAsLong());
    }

    @Test
    void reusedAwaitStartsTimingAndConditionStateAtEachTerminalCall() {
        var time = new FakeTime(100);
        int[] clockReads = {0};
        int[] factories = {0};
        var condition = Conditions.<String>conditionPerWait("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return actual -> ++calls[0] == 2
                    ? ConditionResult.satisfied() : ConditionResult.unsatisfied("first observation");
        });
        var waiting = await(() -> "ready").usingTime(() -> {
            clockReads[0]++;
            return time.getAsLong();
        }, time).every(ofNanos(2)).upTo(ofNanos(10));

        assertEquals(0, clockReads[0]);
        assertEquals(0, factories[0]);
        time.advanceNanos(1000);
        assertEquals("ready", waiting.until(condition));
        assertEquals(1, factories[0]);

        time.advanceNanos(1000);
        var result = diagnostic(waiting).until(condition);
        assertEquals("ready", result.orThrow());
        assertEquals(2, factories[0]);
        assertEquals(2, attempts(result).size());
        assertThat(time.parkRequests).containsExactly(2L, 2L);
        assertEquals(2104, time.getAsLong());
    }

    @Test
    void rejectsMissingTimeOperationsBeforePolling() {
        Callable<String> source = () -> {
            throw new AssertionError("source must not run");
        };
        var waiting = await(source);
        assertThrows(NullPointerException.class, () -> waiting.usingTime(null, time));
        assertThrows(NullPointerException.class, () -> waiting.usingTime(time, null));
    }
}
