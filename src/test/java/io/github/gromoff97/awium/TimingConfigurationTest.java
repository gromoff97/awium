package io.github.gromoff97.awium;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingConfigurationTest {

    @ParameterizedTest
    @ValueSource(longs = {0, Long.MAX_VALUE - 2, Long.MIN_VALUE + 2})
    void persistenceKeepsPositiveElapsedTimeBeyondSignedNanoseconds(long origin) {
        var acquiredAfter = ofNanos(Long.MAX_VALUE - 1);
        for (long persistence : new long[] {2, Long.MAX_VALUE}) {
            for (String outcome : List.of("success", "mismatch", "condition failure", "source failure")) {
                var time = new FakeTime(origin);
                var cause = new IllegalStateException(outcome);
                int[] calls = {0};
                var waiting = await(() -> {
                    if (++calls[0] == 1) time.advanceNanos(Long.MAX_VALUE - 1);
                    else if (outcome.equals("source failure")) throw cause;
                    return "ready";
                }).usingTime(time, time).every(ofNanos(Long.MAX_VALUE))
                        .upTo(ofNanos(Long.MAX_VALUE)).persisting(ofNanos(persistence));
                var result = waiting.tryUntil(Conditions.condition("ready", (String actual) -> {
                    if (calls[0] > 1) {
                        if (outcome.equals("condition failure")) throw cause;
                        if (outcome.equals("mismatch")) return ConditionResult.unsatisfied("changed");
                    }
                    return ConditionResult.satisfied(actual);
                }));

                assertEquals(2, calls[0], outcome);
                assertEquals(2, result.attempts().size(), outcome);
                assertEquals(acquiredAfter, result.attempts().getFirst().timing().completionOffset(), outcome);
                var last = result.attempts().getLast();
                var elapsed = acquiredAfter.plusNanos(persistence);
                assertEquals(AttemptRecord.Phase.PERSISTENCE, last.phase(), outcome);
                assertEquals(acquiredAfter, last.timing().startOffset(), outcome);
                assertEquals(elapsed, last.timing().retrievalOffset(), outcome);
                assertEquals(outcome.equals("source failure") ? null : elapsed,
                        last.timing().observationOffset(), outcome);
                assertEquals(elapsed, last.timing().completionOffset(), outcome);
                if (outcome.equals("success")) {
                    assertEquals("ready", assertInstanceOf(AwaitResult.Satisfied.class, result).result());
                } else {
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                    assertEquals(switch (outcome) {
                        case "mismatch" -> PERSISTENCE_FAILED;
                        case "condition failure" -> CONDITION_FAILED;
                        default -> SOURCE_FAILED;
                    }, failure.reason());
                    assertSame(outcome.equals("mismatch") ? null : cause, failure.cause());
                    assertTrue(failure.message().contains("Elapsed: "
                            + (persistence == 2 ? "106751 days" : "213503 days")), failure.message());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {50, 100})
    void timeoutAtMostTheDefaultIntervalAllowsImmediateSuccessAndOneFailedObservation(long timeoutMillis) {
        var time = new FakeTime(0);
        var timeout = ofMillis(timeoutMillis);
        assertEquals("ready", await(() -> "ready").usingTime(time, time).upTo(timeout).until(isNotNull));
        assertEquals(List.of(), time.parkRequests);

        int[] calls = {0};
        var result = await(() -> ++calls[0]).usingTime(time, time).upTo(timeout).tryUntil(equalTo(2));

        var failure = assertInstanceOf(AwaitResult.Failed.class, result);
        assertEquals(TIMEOUT, failure.failure().reason());
        assertEquals(1, calls[0]);
        assertEquals(1, result.totalAttempts());
        assertEquals(1, result.attempts().size());
        assertEquals(timeout.toNanos(), time.getAsLong());
        assertEquals(List.of(timeout.toNanos()), time.parkRequests);
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
                    ? ConditionResult.satisfied(actual) : ConditionResult.unsatisfied("first observation");
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
        var result = waiting.tryUntil(condition);
        assertEquals("ready", assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(2, factories[0]);
        assertEquals(2, result.totalAttempts());
        assertEquals(ofNanos(0), result.attempts().getFirst().timing().startOffset());
        assertEquals(ofNanos(2), result.attempts().getLast().timing().completionOffset());
    }

    @Test
    void rejectsMissingTimeOperationsBeforePolling() {
        var time = new FakeTime(0);
        Callable<String> source = () -> {
            throw new AssertionError("source must not run");
        };
        var waiting = await(source);
        assertThrows(NullPointerException.class, () -> waiting.usingTime(null, time));
        assertThrows(NullPointerException.class, () -> waiting.usingTime(time, null));
    }
}
