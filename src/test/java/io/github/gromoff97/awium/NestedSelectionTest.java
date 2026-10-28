package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.evaluated;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class NestedSelectionTest {

    record Payment(int id, boolean paid) {}

    @Test
    void nestedConditionsCheckAndNarrowTheSoleValue() {
        var paid = new Payment(2, true);
        var time = new FakeTime(0);
        var waiting = await(() -> List.of(paid)).usingTime(time, time);
        assertSame(paid, waiting.until(singleElement(matches(Payment::paid))));
        assertSame(paid, waiting.until(singleElement(asserted(payment -> { assertTrue(payment.paid()); }))));
        assertEquals(paid, waiting.until(singleElement(equalTo(paid))));
        assertEquals(42, await(() -> List.<Number>of(42)).usingTime(time, time).until(singleElement(instanceOf(Integer.class))));
        assertEquals("key", await(() -> Map.of("key", 42).keySet()).usingTime(time, time).until(singleElement));
        assertNull(await(() -> java.util.Collections.singletonList((String) null)).usingTime(time, time).until(singleElement(isNull)));
    }

    @Test
    void retriesWrongCardinalityAndThenChecksTheSoleValue() {
        var time = new FakeTime(0);
        List<List<Integer>> observations = List.of(List.of(), List.of(42, 42), List.of(1, 42), List.of(1), List.of(42));
        var source = observations.iterator();
        int result = await(source::next).usingTime(time, time).every(ofNanos(1))
                .until(singleElement(equalTo(42)));
        assertEquals(42, result);
        assertFalse(source.hasNext());
        var failed = await(() -> List.of(1)).usingTime(time, time).upTo(ofNanos(1)).tryUntil(singleElement(equalTo(42)));
        var failure = assertInstanceOf(AwaitResult.Failed.class, failed);
        assertTrue(failure.failure().message().contains("42"));
    }

    @Test
    void orderedNestedConditionsCaptureDifferentObservationsAndKeepProgress() {
        var pending = new Payment(1, false);
        var paid = new Payment(1, true);
        var observations = List.of(pending, paid).iterator();
        var time = new FakeTime(0);
        List<List<Payment>> values = await(() -> List.of(observations.next())).usingTime(time, time).every(ofNanos(1)).until(captured(
                singleElement(matches((Payment value) -> !value.paid())),
                singleElement(matches(Payment::paid))));
        assertEquals(List.of(List.of(pending), List.of(paid)), values);
        assertFalse(observations.hasNext());

        var result = await(() -> List.of(pending)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).tryUntil(captured(
                singleElement(matches((Payment value) -> !value.paid())),
                singleElement(matches(Payment::paid))));
        var observation = evaluated(result.attempts().getLast());
        assertInstanceOf(AttemptResult.Unsatisfied.class, observation.result());
        var progress = result.attempts().getLast().progress();
        assertEquals(1, progress.capturedStages());
    }

    @Test
    void eachTerminalAndSequenceStageGetsAFreshLazyCallback() {
        int[] factories = {0};
        PreservingCondition<String> ready = conditionPerWait("ready after retry", () -> {
            ++factories[0];
            int[] calls = {0};
            return value -> ++calls[0] == 2 ? satisfied() : unsatisfied("retry");
        });
        var time = new FakeTime(0);
        for (List<String> invalid : java.util.Arrays.asList(null, List.<String>of(), List.of("ready", "other"))) {
            await(() -> invalid).usingTime(time, time).upTo(ofNanos(1)).tryUntil(singleElement(ready));
        }
        assertEquals(0, factories[0]);
        var waiting = await(() -> List.of("ready")).usingTime(time, time).every(ofNanos(1));
        assertEquals("ready", waiting.until(singleElement(ready)));
        assertEquals(List.of(List.of("ready"), List.of("ready")), waiting.tryUntil(captured(singleElement(ready), singleElement(ready))).orThrow());
        assertEquals(3, factories[0]);
    }

    @Test
    void nestedFailuresStopSelectionAndRetainTheirOriginalCause() {
        for (Throwable cause : List.of(new IOException("checked"), new InterruptedException("interrupted"),
                new IllegalStateException("runtime"), new AssertionError("uncontrolled assertion"))) {
            var time = new FakeTime(0);
            int[] checks = {0};
            try {
                var result = await(() -> List.of("a")).usingTime(time, time).tryUntil(singleElement(condition("broken", (String value) -> {
                    ++checks[0];
                    throw cause;
                })));
                var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                assertSame(cause, failure.failure().cause());
                assertTrue(failure.failure().message().contains("broken"));
                assertEquals(1, checks[0]);
                assertTrue(time.parkRequests.isEmpty());
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void invalidCallbacksAreValidatedAndErrorsBecomeUncontrolled() {
        assertEquals("condition must not be null",
                assertThrows(NullPointerException.class, () -> singleElement((PreservingCondition<String>) null)).getMessage());
        var time = new FakeTime(0);
        var invalid = await(() -> List.of("ready")).usingTime(time, time).tryUntil(singleElement(condition("invalid", (String value) -> null)));
        assertInstanceOf(NullPointerException.class, assertInstanceOf(AwaitResult.Failed.class, invalid).failure().cause());
        var fatal = new OutOfMemoryError("fatal");
        var failed = await(() -> List.of("ready")).usingTime(time, time)
                .tryUntil(singleElement(condition("fatal", value -> { throw fatal; })));
        assertSame(fatal, assertInstanceOf(AwaitResult.Failed.class, failed).failure().cause());
    }
}
