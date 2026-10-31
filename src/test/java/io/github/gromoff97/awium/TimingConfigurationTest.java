package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Satisfied;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class TimingConfigurationTest {

    private final FakeTime time = new FakeTime(0);

    @TestFactory
    Stream<DynamicTest> persistenceKeepsPositiveElapsedTimeBeyondSignedNanoseconds() {
        return Stream.of(0L, Long.MAX_VALUE - 2, Long.MIN_VALUE + 2).flatMap(origin ->
                Stream.of(Map.entry(2L, "106751 d"), Map.entry(Long.MAX_VALUE, "213503 d")).flatMap(timing -> {
                    long persistence = timing.getKey();
                    String label = "origin=" + origin + ", persistence=" + persistence + " / ";
                    return Stream.of(
                            dynamicTest(label + "success", () -> {
                                var result = persistPastSignedNanoseconds(origin, persistence, () -> "ready", () -> satisfied("ready"));
                                assertEquals(1, attempts(result).size());
                                assertInstanceOf(Satisfied.class, attempts(result).getLast());
                                assertEquals("ready", result.orThrow());
                            }),
                            dynamicTest(label + "mismatch", () -> {
                                var result = persistPastSignedNanoseconds(origin, persistence, () -> "ready", () -> unsatisfied("changed"));
                                assertOverflowFailure(result, Unsatisfied.class, PERSISTENCE_FAILED, null, timing.getValue());
                            }),
                            dynamicTest(label + "condition failure", () -> {
                                var cause = new IllegalStateException("condition failure");
                                var result = persistPastSignedNanoseconds(origin, persistence, () -> "ready", () -> { throw cause; });
                                assertOverflowFailure(result, FailedAfterObservation.class, CONDITION_FAILED, cause, timing.getValue());
                            }),
                            dynamicTest(label + "source failure", () -> {
                                var cause = new IllegalStateException("source failure");
                                var result = persistPastSignedNanoseconds(origin, persistence, () -> { throw cause; }, () -> satisfied("ready"));
                                assertOverflowFailure(result, FailedBeforeObservation.class, SOURCE_FAILED, cause, timing.getValue());
                            }));
                }));
    }

    private static AwaitResult<String, String> persistPastSignedNanoseconds(
            long origin, long persistence, Callable<String> nextRead, Supplier<ConditionResult<String>> nextCheck) {
        var time = new FakeTime(origin);
        var observations = List.<Callable<String>>of(() -> {
            time.advanceNanos(Long.MAX_VALUE - 1);
            return "ready";
        }, nextRead).iterator();
        var checks = List.<Supplier<ConditionResult<String>>>of(() -> satisfied("ready"), nextCheck).iterator();
        var waiting = await(() -> observations.next().call()).usingTime(time, time).every(ofNanos(Long.MAX_VALUE))
                .upTo(ofNanos(Long.MAX_VALUE)).persisting(ofNanos(persistence));
        var result = diagnostic(waiting).until(condition("ready", (String actual) -> checks.next().get()));

        assertFalse(observations.hasNext(), "both source calls must run");
        assertEquals(2L, result.conditions().getFirst().attempts().lastKey());
        assertEquals(origin + Long.MAX_VALUE - 1 + persistence, time.getAsLong());
        return result;
    }

    private static void assertOverflowFailure(AwaitResult<String, String> result, Class<?> status, Reason reason, Throwable cause, String elapsed) {
        assertEquals(2, attempts(result).size());
        assertInstanceOf(status, attempts(result).getLast());
        var failure = failure(reason, result);
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("elapsed " + elapsed);
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 100})
    void timeoutAtMostTheDefaultIntervalAllowsImmediateSuccess(long timeoutMillis) {
        var timeout = ofMillis(timeoutMillis);
        assertEquals("ready", await(() -> "ready").usingTime(time, time).upTo(timeout).until(isNotNull));
        assertThat(time.parkRequests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 100})
    void timeoutAtMostTheDefaultIntervalAllowsOnlyOneFailedObservation(long timeoutMillis) {
        var timeout = ofMillis(timeoutMillis);
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time).upTo(timeout).until(equalTo(2));

        var failure = failure(TIMEOUT, result);
        assertEquals(1, calls[0]);
        assertEquals(1, attempts(result).size());
        assertEquals(timeout.toNanos(), time.getAsLong());
        assertThat(time.parkRequests).containsExactly(timeout.toNanos());
    }

    @Test
    void diagnosticMapPersistenceRechecksTheSelectedValue() {
        var states = List.of(Map.of("key", "ready"), Map.of("key", "changed")).iterator();
        var result = tryAwait(states::next).usingTime(time, time).every(ofNanos(1))
                .persisting(ofNanos(2)).until(valueFor("key", equalTo("ready")));

        var failure = failure(PERSISTENCE_FAILED, result);
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
        var condition = Conditions.conditionPerWait("second observation", () -> {
            factories[0]++;
            var outcomes = List.<ConditionResult<String>>of(unsatisfied("first observation"), satisfied("ready")).iterator();
            return (String actual) -> outcomes.next();
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

    @TestFactory
    List<DynamicTest> rejectsMissingTimeOperationsBeforePolling() {
        Callable<String> source = () -> {
            throw new AssertionError("source must not run");
        };
        var waiting = await(source);
        return List.of(
                dynamicTest("missing clock", () -> assertThrows(NullPointerException.class, () -> waiting.usingTime(null, time))),
                dynamicTest("missing parker", () -> assertThrows(NullPointerException.class, () -> waiting.usingTime(time, null))));
    }
}
