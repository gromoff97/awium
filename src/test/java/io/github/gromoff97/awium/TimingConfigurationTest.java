package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.equalTo;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimingConfigurationTest {

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
        assertInstanceOf(AwaitTimeoutException.class, failure.failure());
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
    void rejectsMissingTimeOperationsBeforePolling() {
        var time = new FakeTime(0);
        assertThrows(NullPointerException.class, () -> await((Source<String>) () -> {
            throw new AssertionError("source must not run");
        }).usingTime(null, time));
        assertThrows(NullPointerException.class, () -> await((Source<String>) () -> {
            throw new AssertionError("source must not run");
        }).usingTime(time, null));
    }
}
