package io.github.gromoff97.awium.waiting;

import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.support.FakeTime;
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
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.failure;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.checkPerWait;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                                assertInstanceOf(SatisfiedObservation.class, attempts(result).getLast());
                                assertEquals("ready", result.orThrow());
                            }),
                            dynamicTest(label + "mismatch", () -> {
                                var result = persistPastSignedNanoseconds(origin, persistence, () -> "ready", () -> unsatisfied("changed"));
                                assertOverflowFailure(result, UnsatisfiedObservation.class, PERSISTENCE_FAILED, null, timing.getValue());
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
        var waiting = withTime(await(() -> observations.next().call()), time, time).every(ofNanos(Long.MAX_VALUE))
                .upTo(ofNanos(Long.MAX_VALUE)).persisting(ofNanos(persistence));
        var result = diagnostic(waiting).until(check("ready", (String actual) -> checks.next().get()));

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
        assertEquals("ready", withTime(await(() -> "ready"), time, time).upTo(timeout).until(accepted));
        assertThat(time.parkRequests).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 100})
    void timeoutAtMostTheDefaultIntervalAllowsOnlyOneFailedObservation(long timeoutMillis) {
        var timeout = ofMillis(timeoutMillis);
        int[] calls = {0};
        var result = withTime(tryAwait(() -> ++calls[0]), time, time).upTo(timeout).until(expected(2));

        failure(TIMEOUT, result);
        assertEquals(1, calls[0]);
        assertEquals(1, attempts(result).size());
        assertEquals(timeout.toNanos(), time.getAsLong());
        assertThat(time.parkRequests).containsExactly(timeout.toNanos());
    }

    @Test
    void reusedAwaitStartsTimingAndConditionStateAtEachTerminalCall() {
        var time = new FakeTime(100);
        int[] clockReads = {0};
        int[] factories = {0};
        var condition = checkPerWait("second observation", () -> {
            factories[0]++;
            var outcomes = List.<ConditionResult<String>>of(unsatisfied("first observation"), satisfied("ready")).iterator();
            return (String actual) -> outcomes.next();
        });
        var waiting = withTime(await(() -> "ready"), () -> {
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

}
