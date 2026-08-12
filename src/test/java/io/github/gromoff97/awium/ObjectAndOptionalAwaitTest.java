package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.Awium.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.ObjectConditionProvider.*;
import static io.github.gromoff97.awium.conditioning.providers.OptionalConditionProvider.*;
import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;
import static java.time.Duration.ofNanos;

import io.github.gromoff97.awium.conditioning.*;
import io.github.gromoff97.awium.conditioning.conditions.*;
import io.github.gromoff97.awium.conditioning.providers.ConditionProvider;

import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ObjectAndOptionalAwaitTest {

    @Test
    void voidAndNullableSelectingTerminalsReturnNullOnSuccess() {
        assertNull(await(
                (Source<Object>) () -> null)
                .until(isNull));
        assertNull(await(
                (Source<String>) () -> "value")
                .until(ConditionProvider.<String, String>passed(value -> null)
                        .because("nullable property")));
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();

        assertSame(equalValue, await(
                (OptionalSource<Object>)
                        () -> Optional.of(equalValue))
                .until(hasValueEqualTo(equalValue)));
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        int[] calls = {0};
        FakeTime time = new FakeTime(0);
        Await<Integer> stage = stage(time, () -> ++calls[0]);
        Condition<Integer, Integer> evenObservation = condition(
                "even observation", value -> value % 2 == 0
                        ? satisfied(value)
                        : unsatisfied("odd observation"));

        assertEquals(2, stage.until(evenObservation));
        assertEquals(4, stage.until(evenObservation));
        assertEquals(4, calls[0]);
    }

    @Test
    void reusableStageStartsFreshAfterControlledAndUncontrolledFailures() {
        FakeTime time = new FakeTime(0);
        int[] sourceCalls = {0};
        Await<String> stage = stage(time, () -> {
            sourceCalls[0]++;
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

        assertEquals("value", stage.until(condition(
                "ready", Evaluation::satisfied)));
        assertEquals(5, sourceCalls[0]);
    }

    private static <T> Await<T> stage(
            FakeTime time, Source<T> source) {
        return timedAwait(source,
                defaults().withEvery(ofNanos(1))
                        .withUpTo(ofNanos(3)),
                time, time);
    }

}
