package io.github.gromoff97.awium;

import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.conditions.CollectionConditions.single;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class NestedSelectionTest {

    record Payment(int id, boolean paid) {}

    @Test
    void nestedConditionsSelectTheUniqueMatchingElementJustLikePredicates() {
        var pending = new Payment(1, false);
        var paid = new Payment(2, true);
        var time = new FakeTime(0);
        var waiting = await(() -> List.of(paid, pending)).usingTime(time, time);
        assertSame(paid, waiting.until(single(Payment::paid)));
        assertSame(paid, waiting.until(single(matches(Payment::paid).because("paid payment"))));
        assertSame(paid, waiting.until(single(asserted(payment -> { assertTrue(payment.paid()); }))));
        assertEquals(paid, waiting.until(single(equalTo(paid))));
        assertEquals(42, await(() -> List.<Number>of(1L, 42)).usingTime(time, time).until(single(instanceOf(Integer.class))));
        assertEquals("key", await(() -> Map.of("key", 42)).usingTime(time, time).until(singleEntry(yields(Map.Entry::getKey))));
        assertNull(await(() -> java.util.Collections.singletonList((String) null)).usingTime(time, time).until(single(isNull)));
    }

    @Test
    void retriesEmptyAndAmbiguousSelectionsAndKeepsTheNestedReason() {
        var time = new FakeTime(0);
        var attempts = new AtomicInteger();
        int result = await(() -> switch (attempts.getAndIncrement()) {
            case 0 -> List.<Integer>of();
            case 1 -> List.of(42, 42);
            default -> List.of(1, 42);
        }).usingTime(time, time).every(ofNanos(1)).until(single(equalTo(42).because("required answer")));
        assertEquals(42, result);
        assertEquals(3, attempts.get());
        var failed = await(() -> List.of(1, 2)).usingTime(time, time).upTo(ofNanos(1)).tryUntil(single(equalTo(42).because("required answer")));
        var failure = assertInstanceOf(AwaitResult.Failed.class, failed);
        assertTrue(failure.failure().getMessage().contains("required answer"));
        assertTrue(failure.failure().getMessage().contains("42"));
    }

    @Test
    void orderedNestedConditionsCaptureDifferentObservationsAndKeepStageReasons() {
        var pending = new Payment(1, false);
        var paid = new Payment(1, true);
        var calls = new AtomicInteger();
        var time = new FakeTime(0);
        List<Payment> values = await(() -> List.of(calls.getAndIncrement() == 0 ? pending : paid)).usingTime(time, time).every(ofNanos(1)).until(
                single(matches((Payment value) -> !value.paid()).because("creation")),
                single(matches(Payment::paid).because("payment")));
        assertEquals(List.of(pending, paid), values);
        assertEquals(2, calls.get());

        var result = await(() -> List.of(pending)).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3)).tryUntil(
                single(matches((Payment value) -> !value.paid()).because("creation")),
                single(matches(Payment::paid).because("payment")));
        var outcome = assertInstanceOf(AwaitAttempt.Outcome.Unsatisfied.class, result.attempts().getLast().outcome());
        var context = assertInstanceOf(AwaitAttempt.Context.Sequence.class, outcome.context());
        assertEquals(1, context.capturedStages());
        assertEquals("payment", context.importance());
    }

    @Test
    void eachTerminalAndSequenceStageGetsAFreshLazyCallback() {
        var factories = new AtomicInteger();
        Condition<String, Integer> length = conditionFactory("length after retry", () -> {
            factories.incrementAndGet();
            var calls = new AtomicInteger();
            return value -> calls.incrementAndGet() == 2 ? satisfied(value.length()) : unsatisfied("retry");
        });
        var time = new FakeTime(0);
        await(() -> List.<String>of()).usingTime(time, time).upTo(ofNanos(1)).tryUntil(single(length));
        assertEquals(0, factories.get());
        var waiting = await(() -> List.of("ready")).usingTime(time, time).every(ofNanos(1));
        assertEquals(5, waiting.until(single(length)));
        assertEquals(List.of(5, 5), assertInstanceOf(AwaitResult.Satisfied.class, waiting.tryUntil(single(length), single(length))).result());
        assertEquals(3, factories.get());
    }

    @Test
    void nestedFailuresStopSelectionAndRetainTheirOriginalCause() {
        for (Throwable cause : List.of(new IOException("checked"), new InterruptedException("interrupted"),
                new IllegalStateException("runtime"), new AssertionError("uncontrolled assertion"))) {
            var time = new FakeTime(0);
            var checks = new AtomicInteger();
            try {
                var result = await(() -> List.of("a", "b")).usingTime(time, time).tryUntil(single(condition("broken", (String value) -> {
                    checks.incrementAndGet();
                    if (cause instanceof Error error) throw error;
                    throw (Exception) cause;
                }).because("reason")));
                var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                assertSame(cause, failure.failure().getCause());
                assertTrue(failure.failure().getMessage().contains("reason"));
                assertEquals(1, checks.get());
                assertTrue(time.parkRequests.isEmpty());
                assertEquals(cause instanceof InterruptedException, Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void everySelectionStopsBeforeTheNextCallbackAfterInterruption() {
        List<Function<Predicate<Integer>, AwaitResult<?, ?>>> waits = List.of(
                predicate -> await(() -> List.of(1, 2)).tryUntil(single(predicate)),
                predicate -> await(() -> List.of(1, 2)).tryUntil(single(matches(predicate))),
                predicate -> await(() -> Map.of("a", 1, "b", 2)).tryUntil(singleEntry((key, value) -> predicate.test(value))),
                predicate -> await(() -> Map.of("a", 1, "b", 2)).tryUntil(singleEntry(matches(
                        (Map.Entry<String, Integer> entry) -> predicate.test(entry.getValue())))));
        for (var wait : waits) {
            for (boolean matching : List.of(false, true)) {
                var calls = new AtomicInteger();
                try {
                    var result = wait.apply(value -> {
                        calls.incrementAndGet();
                        Thread.currentThread().interrupt();
                        return matching;
                    });
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                    assertInstanceOf(InterruptedException.class, failure.failure().getCause());
                    assertTrue(Thread.currentThread().isInterrupted());
                    assertEquals(1, calls.get());
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void invalidCallbacksAreValidatedAndFatalErrorsEscape() {
        assertThrows(NullPointerException.class, () -> single((PreservingCondition<String>) null));
        var time = new FakeTime(0);
        var invalid = await(() -> List.of("ready")).usingTime(time, time).tryUntil(single(condition("invalid", (String value) -> null)));
        assertInstanceOf(NullPointerException.class, assertInstanceOf(AwaitResult.Failed.class, invalid).failure().getCause());
        var fatal = new OutOfMemoryError("fatal");
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
                () -> await(() -> List.of("ready")).usingTime(time, time).tryUntil(single(yields(value -> { throw fatal; })))));
    }
}
