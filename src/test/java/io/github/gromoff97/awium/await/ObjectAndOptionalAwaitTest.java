package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.FakeTime;
import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditions.Conditions;
import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.await.Await.tryAwait;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
import static io.github.gromoff97.awium.internal.engine.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;
import static java.time.Duration.ofNanos;


import io.github.gromoff97.awium.exceptions.*;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ObjectAndOptionalAwaitTest {

    @Test
    void awaitReturnsTheObservationFromABroadPreservingCondition() {
        Source<String> source = () -> "observed";

        assertEquals("observed", await(source).until(broadPreservingCondition()));
    }

    @Test
    void tryAwaitReturnsTheObservationFromABroadPreservingCondition() {
        Source<String> source = () -> "observed";

        AwaitResult<String, String> result = tryAwait(source).until(broadPreservingCondition());
        assertInstanceOf(AwaitResult.Satisfied.class, result);
        assertEquals("observed", ((AwaitResult.Satisfied<?, ?>) result).result());
    }

    @Test
    void voidAndNullableSelectingTerminalsReturnNullOnSuccess() {
        assertNull(await((Source<Object>) () -> null).until(isNull));
        assertNull(await((Source<String>) () -> "value").until(yields(value -> {
            return null;
        }).because("nullable property")));
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();

        assertSame(equalValue, await((OptionalSource<Object>)
                () -> Optional.of(equalValue)).until(hasValue(equalValue)));
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        int[] calls = {0};
        FakeTime time = new FakeTime(0);
        Await<Integer, Integer, Source<?>> stage = stage(time, () -> ++calls[0]);
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
        Await<String, String, Source<?>> stage = stage(time, () -> {
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
                "ready", ConditionEvaluation::satisfied)));
        assertEquals(5, sourceCalls[0]);
    }

    private static <T> Await<T, T, Source<?>> stage(
            FakeTime time, Source<T> source) {
        return timedAwait(source, defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(3)), time, time);
    }

    private static PreservingCondition<Object> broadPreservingCondition() {
        return Conditions.asserted(actual -> {});
    }
}
