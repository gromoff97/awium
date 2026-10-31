package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.assertUnsatisfied;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.probe;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NestedSelectionTest {

    record Payment(int id, boolean paid) {}

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void nestedConditionsCheckAndNarrowTheSoleValue() {
        var paid = new Payment(2, true);
        var waiting = await(() -> List.of(paid));
        assertSame(paid, waiting.until(singleElement(matches(Payment::paid))));
        assertSame(paid, waiting.until(singleElement(asserted(payment -> { assertTrue(payment.paid()); }))));
        assertEquals(paid, waiting.until(singleElement(equalTo(paid))));
        assertEquals(42, await(() -> List.<Number>of(42)).until(singleElement(instanceOf(Integer.class))));
        assertEquals("key", await(() -> Map.of("key", 42).keySet()).until(singleElement));
        assertNull(await(() -> java.util.Collections.singletonList((String) null)).until(singleElement(isNull)));
    }

    @Test
    void retriesWrongCardinalityAndThenChecksTheSoleValue() {
        List<List<Integer>> observations = List.of(List.of(), List.of(42, 42), List.of(1, 42), List.of(1), List.of(42));
        var source = observations.iterator();
        int result = await(source::next).every(ofNanos(1))
                .until(singleElement(equalTo(42)));
        assertEquals(42, result);
        assertFalse(source.hasNext());
        var check = singleElement(equalTo(42));
        rejection(check, List.of(1));
        assertEquals(42, check.references().getFirst().value());
    }

    @Test
    void orderedNestedConditionsCaptureDifferentObservationsAndKeepProgress() {
        var pending = new Payment(1, false);
        var paid = new Payment(1, true);
        var observations = List.of(pending, paid).iterator();
        List<List<Payment>> values = await(() -> List.of(observations.next())).every(ofNanos(1)).until(captured(
                singleElement(matches((Payment value) -> !value.paid())),
                singleElement(matches(Payment::paid))));
        assertEquals(List.of(List.of(pending), List.of(paid)), values);
        assertFalse(observations.hasNext());

        var run = ConditionExecution.<List<Payment>, List<List<Payment>>>start(captured(
                singleElement(matches((Payment value) -> !value.paid())), singleElement(matches(Payment::paid))), false);
        probe(run, List.of(pending));
        assertUnsatisfied(probe(run, List.of(pending)));
        assertUnsatisfied(probe(run, List.of(pending)));
        assertEquals(List.of(List.of(pending)), run.result());
        assertFalse(run.complete());
    }

    @Test
    void eachTerminalAndSequenceStageGetsAFreshLazyCallback() {
        int[] factories = {0};
        Condition<String, String> ready = conditionPerWait("ready after retry", () -> {
            ++factories[0];
            int[] calls = {0};
            return value -> ++calls[0] == 2 ? satisfied(value) : unsatisfied("retry");
        });
        for (List<String> invalid : java.util.Arrays.asList(null, List.<String>of(), List.of("ready", "other"))) {
            rejection(singleElement(ready), invalid);
        }
        assertEquals(0, factories[0]);
        var waiting = await(() -> List.of("ready")).every(ofNanos(1));
        assertEquals("ready", waiting.until(singleElement(ready)));
        assertEquals(List.of(List.of("ready"), List.of("ready")), tryAwait(() -> List.of("ready")).every(ofNanos(1)).until(captured(singleElement(ready), singleElement(ready))).orThrow());
        assertEquals(3, factories[0]);
    }

    @ParameterizedTest
    @MethodSource("nestedFailures")
    void nestedFailuresStopSelectionAndRetainTheirOriginalCause(Throwable cause) {
        int[] checks = {0};
        var result = tryAwait(() -> List.of("a")).until(singleElement(condition("broken", (String value) -> {
            ++checks[0];
            throw cause;
        })));
        var failure = failure(result);
        assertSame(cause, failure.cause());
        assertThat(failure.message()).contains("broken");
        assertEquals(1, checks[0]);

        assertFalse(currentThread().isInterrupted());
    }

    private static List<Throwable> nestedFailures() {
        return List.of(new IOException("checked"), new InterruptedException("interrupted"),
                new IllegalStateException("runtime"), new AssertionError("uncontrolled assertion"));
    }

    @Test
    void invalidCallbacksAreValidatedAndErrorsBecomeUncontrolled() {
        assertEquals("condition must not be null",
                assertThrows(NullPointerException.class, () -> singleElement((PreservingCondition<String>) null)).getMessage());
        var invalid = tryAwait(() -> List.of("ready")).until(singleElement(condition("invalid", (String value) -> null)));
        assertInstanceOf(NullPointerException.class, failure(invalid).cause());
        var fatal = new OutOfMemoryError("fatal");
        var failed = tryAwait(() -> List.of("ready"))
                .until(singleElement(condition("fatal", value -> { throw fatal; })));
        assertSame(fatal, failure(failed).cause());
    }
}
