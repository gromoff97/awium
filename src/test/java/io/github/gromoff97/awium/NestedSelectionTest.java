package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
import static io.github.gromoff97.awium.MapConditions.awaitMap;
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class NestedSelectionTest {

    record Payment(int id, boolean paid) {}

    @Test
    void onlyValueForAcceptsChecksComparisonsAndDeclaredResults() {
        var paid = new Payment(1, true);
        var source = Map.of("payment", paid);
        Payment checked = awaitMap(() -> source).until(MapConditions.onlyValueFor("payment",
                matches(Payment::paid).because("paid payment")).because("only payment"));
        assertSame(paid, checked);
        assertSame(paid, awaitMap(() -> source).until(MapConditions.onlyValueFor("payment", Payment::paid)));
        assertSame(paid, awaitMap(() -> source).until(MapConditions.onlyValueFor("payment", equalTo(paid))));
        Map<String, Object> broad = Map.of("payment", paid);
        Payment narrowed = awaitMap(() -> broad).until(MapConditions.onlyValueFor("payment", instanceOf(Payment.class)));
        assertSame(paid, narrowed);
        var captured = awaitMap(() -> source).tryUntil(MapConditions.onlyValueFor("payment", Payment::paid));
        assertSame(paid, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
    }

    @Test
    void onlyValueForChecksStructureBeforeCreatingNestedStateAndKeepsFailures() {
        var paid = new Payment(1, true);
        int[] factories = {0};
        var cause = new IOException("payment failed");
        var check = conditionPerWait("broken payment", () -> {
            factories[0]++;
            return (Payment payment) -> { throw cause; };
        });
        var time = new FakeTime(0);
        for (Map<String, Payment> source : java.util.Arrays.asList(null, Map.<String, Payment>of(),
                Map.of("other", paid), Map.of("payment", paid, "other", paid))) {
            var result = awaitMap(() -> source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1))
                    .tryUntil(MapConditions.onlyValueFor("payment", check).because("required payment"));
            var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
            assertEquals(AwaitFailure.Reason.TIMEOUT, failure.reason());
            assertTrue(failure.message().contains("Expected: payment"), failure.message());
            assertTrue(failure.message().contains("Because: required payment"), failure.message());
        }
        assertEquals(0, factories[0]);
        var result = awaitMap(() -> Map.of("payment", paid)).tryUntil(MapConditions.onlyValueFor("payment", check));
        assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, result).failure().cause());
        assertEquals(1, factories[0]);
    }

    @Test
    void nestedConditionsSelectAndNarrowTheUniqueMatchingValue() {
        var pending = new Payment(1, false);
        var paid = new Payment(2, true);
        var time = new FakeTime(0);
        var waiting = awaitCollection(() -> List.of(paid, pending)).usingTime(time, time);
        assertSame(paid, waiting.until(single(matches(Payment::paid).because("paid payment"))));
        assertSame(paid, waiting.until(single(asserted(payment -> { assertTrue(payment.paid()); }))));
        assertEquals(paid, waiting.until(single(equalTo(paid))));
        assertEquals(42, awaitCollection(() -> List.<Number>of(1L, 42)).usingTime(time, time).until(single(instanceOf(Integer.class))));
        assertEquals("key", awaitCollection(() -> Map.of("key", 42).keySet()).usingTime(time, time).until(single));
        assertNull(awaitCollection(() -> java.util.Collections.singletonList((String) null)).usingTime(time, time).until(single(isNull)));
    }

    @Test
    void retriesEmptyAndAmbiguousSelectionsAndKeepsTheNestedReason() {
        var time = new FakeTime(0);
        List<List<Integer>> observations = List.of(List.of(), List.of(42, 42), List.of(1, 42));
        var source = observations.iterator();
        int result = awaitCollection(source::next).usingTime(time, time).every(ofNanos(1))
                .until(single(equalTo(42).because("required answer")));
        assertEquals(42, result);
        assertFalse(source.hasNext());
        var failed = awaitCollection(() -> List.of(1, 2)).usingTime(time, time).upTo(ofNanos(1)).tryUntil(single(equalTo(42).because("required answer")));
        var failure = assertInstanceOf(AwaitResult.Failed.class, failed);
        assertTrue(failure.failure().message().contains("required answer"));
        assertTrue(failure.failure().message().contains("42"));
    }

    @Test
    void orderedNestedConditionsCaptureDifferentObservationsAndKeepStageReasons() {
        var pending = new Payment(1, false);
        var paid = new Payment(1, true);
        var observations = List.of(pending, paid).iterator();
        var time = new FakeTime(0);
        List<Payment> values = awaitCollection(() -> List.of(observations.next())).usingTime(time, time).every(ofNanos(1)).until(
                single(matches((Payment value) -> !value.paid()).because("creation")),
                single(matches(Payment::paid).because("payment")));
        assertEquals(List.of(pending, paid), values);
        assertFalse(observations.hasNext());

        var result = awaitCollection(() -> List.of(pending)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).tryUntil(
                single(matches((Payment value) -> !value.paid()).because("creation")),
                single(matches(Payment::paid).because("payment")));
        var observation = assertInstanceOf(AttemptRecord.Outcome.Evaluated.class, result.attempts().getLast().outcome());
        assertInstanceOf(ConditionResult.Unsatisfied.class, observation.evaluation());
        var progress = result.attempts().getLast().progress();
        assertEquals(1, progress.capturedStages());
        assertEquals("payment", progress.activeCondition().explanation());
    }

    @Test
    void eachTerminalAndSequenceStageGetsAFreshLazyCallback() {
        int[] factories = {0};
        PreservingCondition<String> ready = conditionPerWait("ready after retry", () -> {
            ++factories[0];
            int[] calls = {0};
            return value -> ++calls[0] == 2 ? satisfied(value) : unsatisfied("retry");
        });
        var time = new FakeTime(0);
        awaitCollection(() -> List.<String>of()).usingTime(time, time).upTo(ofNanos(1)).tryUntil(single(ready));
        assertEquals(0, factories[0]);
        var waiting = awaitCollection(() -> List.of("ready")).usingTime(time, time).every(ofNanos(1));
        assertEquals("ready", waiting.until(single(ready)));
        assertEquals(List.of("ready", "ready"), assertInstanceOf(AwaitResult.Satisfied.class, waiting.tryUntil(single(ready), single(ready))).result());
        assertEquals(3, factories[0]);
    }

    @Test
    void nestedFailuresStopSelectionAndRetainTheirOriginalCause() {
        for (Throwable cause : List.of(new IOException("checked"), new InterruptedException("interrupted"),
                new IllegalStateException("runtime"), new AssertionError("uncontrolled assertion"))) {
            var time = new FakeTime(0);
            int[] checks = {0};
            try {
                var result = awaitCollection(() -> List.of("a", "b")).usingTime(time, time).tryUntil(single(condition("broken", (String value) -> {
                    ++checks[0];
                    throw cause;
                }).because("reason")));
                var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                assertSame(cause, failure.failure().cause());
                assertTrue(failure.failure().message().contains("reason"));
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
                assertThrows(NullPointerException.class, () -> single((PreservingCondition<String>) null)).getMessage());
        var time = new FakeTime(0);
        var invalid = awaitCollection(() -> List.of("ready")).usingTime(time, time).tryUntil(single(condition("invalid", (String value) -> null)));
        assertInstanceOf(NullPointerException.class, assertInstanceOf(AwaitResult.Failed.class, invalid).failure().cause());
        var fatal = new OutOfMemoryError("fatal");
        var failed = awaitCollection(() -> List.of("ready")).usingTime(time, time)
                .tryUntil(single(condition("fatal", value -> { throw fatal; })));
        assertSame(fatal, assertInstanceOf(AwaitResult.Failed.class, failed).failure().cause());
    }
}
