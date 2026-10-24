package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Await.OptionalSource;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ObjectAndOptionalAwaitTest {

    @Test
    void comparisonsPreserveTheObservedTypeAndNarrowingCanFailAtRuntime() {
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time).upTo(ofNanos(1));
        for (var condition : List.of(equalTo("different"), sameAs("different"), in("one", "two"))) {
            AwaitResult<String, String> result = waiting.tryUntil(condition);
            assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
        }
        String unequal = waiting.until(notEqualTo("different"));
        String different = waiting.until(notSameAs("different"));
        String excluded = waiting.until(notIn("one", "two"));
        AwaitResult<String, Integer> narrowed = waiting.tryUntil(instanceOf(Integer.class));
        AwaitResult<Optional<String>, Integer> nested = await(() -> Optional.of("ready"))
                .usingTime(time, time).upTo(ofNanos(1)).tryUntil(hasValue(instanceOf(Integer.class)));
        Number broadened = await(() -> 1).until(instanceOf(Number.class));

        assertEquals(List.of("ready", "ready", "ready"), List.of(unequal, different, excluded));
        assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, narrowed).failure().reason());
        assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, nested).failure().reason());
        assertEquals(1, broadened);
    }

    @Test
    void matchingConditionsRetryAndReturnTheExactSuccessfulObservationInBothTerminals() {
        var time = new FakeTime(0);
        var first = new Object();
        var ready = new Object();
        var observations = new Object[]{first, ready, first, ready};
        int[] calls = {0};
        var waiting = await(() -> observations[calls[0]++]).usingTime(time, time).every(ofNanos(2)).upTo(ofNanos(5));

        assertSame(ready, waiting.until(matches(actual -> actual == ready)));
        var result = waiting.tryUntil(matches(ready::equals));

        assertSame(ready, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(2, result.totalAttempts());
        assertEquals(4, calls[0]);
        assertEquals(List.of(2L, 2L), time.parkRequests);
    }

    @Test
    void awaitReturnsTheObservationFromABroadPreservingCondition() {
        var pollingTime = new FakeTime(0);
        Callable<String> source = () -> "observed";

        assertEquals("observed", await(source).usingTime(pollingTime, pollingTime).until(broadPreservingCondition()));
    }

    @Test
    void tryUntilReturnsTheObservationFromABroadPreservingCondition() {
        var pollingTime = new FakeTime(0);
        Callable<String> source = () -> "observed";

        AwaitResult<String, String> result = await(source).usingTime(pollingTime, pollingTime).tryUntil(broadPreservingCondition());
        assertInstanceOf(AwaitResult.Satisfied.class, result);
        assertEquals("observed", ((AwaitResult.Satisfied<?, ?>) result).result());
    }

    @Test
    void nullablePreservingTerminalsReturnNullOnSuccess() {
        var pollingTime = new FakeTime(0);
        assertNull(await((Callable<Object>) () -> null).usingTime(pollingTime, pollingTime).until(isNull));
        assertNull(await((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).until(matches(actual -> actual == null)));
        assertNull(await((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).until(condition("nullable property", ConditionResult::satisfied).because("nullable property")));
    }

    @Test
    void capturesNullThenPresentWithTheObservedTypeAndExplanations() {
        String[] observations = {null, "ready", null, "ready"};
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations[reads[0]++]).usingTime(time, time).every(ofNanos(1));

        List<String> values = waiting.until(isNull.because("initially missing"), isNotNull.because("then created"));
        AwaitResult<String, List<String>> captured = waiting.tryUntil(
                isNull.because("initially missing"), isNotNull.because("then created"));

        assertNull(values.getFirst());
        assertEquals("ready", values.getLast());
        assertEquals(values, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        assertEquals(2, captured.totalAttempts());
        assertEquals(4, reads[0]);
    }

    @Test
    void nestedAbsenceChecksPreserveTheSelectedTypes() {
        var time = new FakeTime(0);
        var items = Arrays.asList("value", null);
        Optional<String> empty = Optional.empty();

        String missing = await(() -> items).usingTime(time, time).upTo(ofNanos(1))
                .until(CollectionConditions.single(isNull).because("one missing value"));
        Optional<String> nested = await(() -> Optional.of(empty)).usingTime(time, time).upTo(ofNanos(1))
                .until(hasValue(absent).because("inner optional is empty"));
        Optional<String> mapped = await(() -> Map.of("key", empty)).usingTime(time, time).upTo(ofNanos(1))
                .until(MapConditions.valueFor("key", absent).because("mapped optional is empty"));

        assertNull(missing);
        assertSame(empty, nested);
        assertSame(empty, mapped);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var pollingTime = new FakeTime(0);
        var equalValue = new Object();

        assertSame(equalValue, await((OptionalSource<Object>)
                () -> Optional.of(equalValue)).usingTime(pollingTime, pollingTime).until(hasValue(equalTo(equalValue))));
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        int[] calls = {0};
        FakeTime time = new FakeTime(0);
        ValueAwait<Integer> stage = stage(time, () -> ++calls[0]);
        PreservingCondition<Integer> evenObservation = condition(
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
        ValueAwait<String> stage = stage(time, () -> {
            sourceCalls[0]++;
            return "value";
        });
        PreservingCondition<String> never = condition(
                "never", value -> unsatisfied("not yet"));
        var failure = new IllegalStateException("condition failed");
        PreservingCondition<String> broken = condition(
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

    private static <T> ValueAwait<T> stage(
            FakeTime time, Callable<T> source) {
        return timedAwait(source, DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(3)), time, time);
    }

    private static PreservingCondition<Object> broadPreservingCondition() {
        return Conditions.asserted(actual -> {});
    }
}
