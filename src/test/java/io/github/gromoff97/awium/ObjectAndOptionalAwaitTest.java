package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObjectAndOptionalAwaitTest {

    @Test
    void voidAndNullableSelectingTerminalsReturnNullOnSuccess() {
        Void nullValue = await(
                (Source<Object>) () -> null)
                .until(isNull);
        Void absentValue = await(
                (OptionalSource<Object>) Optional::empty)
                .until(absent);
        String selectedNull = await(
                (Source<String>) () -> "value")
                .until(ConditionProvider.<String, String>passed(value -> null)
                        .because("nullable property"));

        assertNull(nullValue);
        assertNull(absentValue);
        assertNull(selectedNull);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();
        var differentValue = new Object();

        Object equal = await(
                (OptionalSource<Object>)
                        () -> Optional.of(equalValue))
                .until(hasValueEqualTo(equalValue));
        Object different = await(
                (OptionalSource<Object>)
                        () -> Optional.of(differentValue))
                .until(hasValueNotEqualTo(equalValue)
                        .because("different value"));

        assertSame(equalValue, equal);
        assertSame(differentValue, different);
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        AtomicInteger calls = new AtomicInteger();
        Source<String> source = () -> "v" + calls.incrementAndGet();
        FakeTime time = new FakeTime(0);
        AwaitStage<String> stage = new AwaitStage<>(source,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(10)),
                time, time);
        Condition<String, String> evenObservation = condition(
                "even observation", value -> Integer.parseInt(value.substring(1)) % 2 == 0
                        ? satisfied(value)
                        : unsatisfied("odd observation"));

        assertEquals("v2", stage.until(evenObservation));
        assertEquals("v4", stage.until(evenObservation));
        assertEquals(4, calls.get());
    }

    @Test
    void reusableStageStartsFreshAfterControlledAndUncontrolledFailures() {
        FakeTime time = new FakeTime(0);
        AtomicInteger sourceCalls = new AtomicInteger();
        Await<String> stage = stage(time, () -> {
            sourceCalls.incrementAndGet();
            return "value";
        });
        Condition<String, String> never = condition(
                "never", value -> unsatisfied("not yet"));
        var failure = new IllegalStateException("condition failed");
        Condition<String, String> broken = condition(
                "broken", value -> {
                    throw failure;
                });

        assertThrows(AwaitTimeoutException.class, () -> stage.until(never));
        assertSame(failure, assertThrows(
                AwaitConditionEvaluationException.class,
                () -> stage.until(broken)).getCause());
        String result = stage.until(condition(
                "ready", Evaluation::satisfied));

        assertEquals("value", result);
        assertEquals(5, sourceCalls.get());
    }

    @Test
    void reusableConditionRetainsOnlyItsOwnStateAcrossFreshWaits() {
        FakeTime time = new FakeTime(0);
        AtomicInteger evaluations = new AtomicInteger();
        Await<String> stage = stage(time, () -> "value");
        Condition<String, String> secondEvaluationOnward =
                condition("second evaluation onward", value ->
                        evaluations.incrementAndGet() >= 2
                                ? satisfied(value)
                                : unsatisfied("first evaluation"));

        assertEquals("value", stage.until(secondEvaluationOnward));
        assertEquals("value", stage.until(secondEvaluationOnward));
        assertEquals(3, evaluations.get());
    }

    private static <T> Await<T> stage(
            FakeTime time, Source<T> source) {
        return new AwaitStage<>(source,
                defaults().withEvery(Duration.ofNanos(1))
                        .withUpTo(Duration.ofNanos(3)),
                time, time);
    }

}
