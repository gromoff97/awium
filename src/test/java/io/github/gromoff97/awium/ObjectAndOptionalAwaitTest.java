package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;

import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.WaitConfiguration.defaults;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static java.time.Duration.ofNanos;

import io.github.gromoff97.awium.*;
import io.github.gromoff97.awium.Source.OptionalSource;

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
        var pollingTime = new FakeTime(0);
        Source<String> source = () -> "observed";

        assertEquals("observed", await(source).usingTime(pollingTime, pollingTime).until(broadPreservingCondition()));
    }

    @Test
    void tryUntilReturnsTheObservationFromABroadPreservingCondition() {
        var pollingTime = new FakeTime(0);
        Source<String> source = () -> "observed";

        AwaitResult<String, String> result = await(source).usingTime(pollingTime, pollingTime).tryUntil(broadPreservingCondition());
        assertInstanceOf(AwaitResult.Satisfied.class, result);
        assertEquals("observed", ((AwaitResult.Satisfied<?, ?>) result).result());
    }

    @Test
    void voidAndNullableSelectingTerminalsReturnNullOnSuccess() {
        var pollingTime = new FakeTime(0);
        assertNull(await((Source<Object>) () -> null).usingTime(pollingTime, pollingTime).until(isNull));
        assertNull(await((Source<String>) () -> "value").usingTime(pollingTime, pollingTime).until(yields(value -> {
            return null;
        }).because("nullable property")));
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var pollingTime = new FakeTime(0);
        var equalValue = new Object();

        assertSame(equalValue, await((OptionalSource<Object>)
                () -> Optional.of(equalValue)).usingTime(pollingTime, pollingTime).until(hasValue(equalValue)));
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

        assertFailure(TIMEOUT, () -> stage.until(never));
        assertSame(failure, assertFailure(CONDITION_FAILED,
                () -> stage.until(broken)).getCause());

        assertEquals("value", stage.until(condition(
                "ready", ConditionResult::satisfied)));
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
