package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.time.Duration.ofNanos;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectAndOptionalAwaitTest {

    @Test
    void readyCandidateCollectionsPreserveScalarAndContainerTypes() {
        var text = await(() -> "ready").until(in(List.of("pending", "ready")));
        var list = List.of("ready");
        var map = Map.of("ready", 1);
        var optional = Optional.of("ready");
        var selectedList = await(() -> list).until(in(List.of(List.of("ready"))));
        var selectedMap = await(() -> map).until(in(List.of(Map.of("ready", 1))));
        var selectedOptional = await(() -> optional).until(in(List.of(Optional.of("ready"))));
        AwaitResult<Map<String, Integer>, Map<String, Integer>> captured = await(() -> map).tryUntil(
                notIn(List.of(Map.of("failed", 0))));

        assertEquals(5, text.length());
        assertSame(list, selectedList);
        assertEquals(5, selectedList.getFirst().length());
        assertSame(map, selectedMap);
        assertEquals(1, selectedMap.get("ready").intValue());
        assertSame(optional, selectedOptional);
        assertEquals(5, selectedOptional.orElseThrow().length());
        assertSame(map, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
    }

    @Test
    void arrayCandidatesRemainWholeValuesInBothTerminalsAndSequences() {
        String[] empty = {};
        String[] ready = {"ready"};
        for (var actual : List.of(empty, ready)) {
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time);
            var value = waiting.until(in(singletonList(actual.clone())));
            AwaitResult<String[], String[]> captured = waiting.tryUntil(
                    in(singletonList(actual.clone())));
            var values = waiting.until(in(singletonList(actual.clone())),
                    notIn(singletonList(new String[]{"failed"})));

            assertSame(actual, value);
            assertEquals(actual.length, value.length);
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
            assertSame(actual, values.getFirst());
            assertEquals(actual.length, values.getLast().length);
            if (value.length > 0) assertEquals(5, value[0].length());
        }
        int[] actual = {1, 2};
        var selected = await(() -> actual).until(in(singletonList(actual.clone())));
        assertSame(actual, selected);
        assertEquals(1, selected[0]);
    }

    @Test
    void candidateCollectionsMayBeEmptyButNotNull() {
        assertThrows(NullPointerException.class, () -> in(null));
        assertThrows(NullPointerException.class, () -> notIn(null));
        for (String actual : Arrays.asList(null, "", "ready")) {
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            String excluded = waiting.until(notIn(List.of()));
            assertSame(actual, excluded);
            AwaitResult<String, String> captured = waiting.tryUntil(notIn(List.of()));
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());

            var failed = waiting.tryUntil(in(List.of()));
            assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, failed).failure().reason());
            assertEquals(List.of(), failed.attempts().getLast().condition().reference().value());
            assertFailure(TIMEOUT, () -> waiting.until(in(List.of())));
        }
    }

    @Test
    void comparisonsPreserveTheObservedTypeAndNarrowingCanFailAtRuntime() {
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time).upTo(ofNanos(1));
        for (var condition : List.of(equalTo("different"), sameAs("different"), in(List.of("one", "two")))) {
            AwaitResult<String, String> result = waiting.tryUntil(condition);
            assertEquals(TIMEOUT, assertInstanceOf(AwaitResult.Failed.class, result).failure().reason());
        }
        String unequal = waiting.until(notEqualTo("different"));
        String different = waiting.until(notSameAs("different"));
        String excluded = waiting.until(notIn(List.of("one", "two")));
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
    void broadPreservingConditionsRetainTheConcreteObservationInBothTerminals() {
        var time = new FakeTime(0);
        var actual = new String("observed");
        var waiting = await(() -> actual).usingTime(time, time);
        PreservingCondition<Object> condition = Conditions.asserted(ignored -> {});

        String value = waiting.until(condition);
        AwaitResult<String, String> result = waiting.tryUntil(condition);

        assertSame(actual, value);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
    }

    @Test
    void nullablePreservingTerminalsReturnNullOnSuccess() {
        var pollingTime = new FakeTime(0);
        assertNull(await((Callable<Object>) () -> null).usingTime(pollingTime, pollingTime).until(isNull));
        assertNull(await((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).until(matches(actual -> actual == null)));
        assertNull(await((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).until(condition("nullable property", ConditionResult::satisfied)));
    }

    @Test
    void capturesNullThenPresentWithTheObservedType() {
        String[] observations = {null, "ready", null, "ready"};
        int[] reads = {0};
        var time = new FakeTime(0);
        var waiting = await(() -> observations[reads[0]++]).usingTime(time, time).every(ofNanos(1));

        List<String> values = waiting.until(isNull, isNotNull);
        AwaitResult<String, List<String>> captured = waiting.tryUntil(
                isNull, isNotNull);

        assertNull(values.getFirst());
        assertEquals("ready", values.getLast());
        assertEquals(values, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        assertEquals(2, captured.totalAttempts());
        assertEquals(4, reads[0]);
    }

    @Test
    void nestedAbsenceChecksPreserveTheSelectedTypes() {
        var time = new FakeTime(0);
        var items = java.util.Collections.singletonList((String) null);
        Optional<String> empty = Optional.empty();

        String missing = await(() -> items).usingTime(time, time).upTo(ofNanos(1))
                .until(CollectionConditions.singleElement(isNull));
        Optional<String> nested = await(() -> Optional.of(empty)).usingTime(time, time).upTo(ofNanos(1))
                .until(hasValue(absent));
        Optional<String> mapped = await(() -> Map.of("key", empty)).usingTime(time, time).upTo(ofNanos(1))
                .until(MapConditions.valueFor("key", absent));

        assertNull(missing);
        assertSame(empty, nested);
        assertSame(empty, mapped);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var pollingTime = new FakeTime(0);
        var equalValue = new Object();

        assertSame(equalValue, await(() -> Optional.of(equalValue)).usingTime(pollingTime, pollingTime)
                .until(hasValue(equalTo(equalValue))));
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

    @Test
    void narrowingWaitsForANullSourceToProduceAValue() {
        for (var check : List.of(Conditions.instanceOf(String.class), Conditions.exactlyInstanceOf(String.class))) {
            var observations = java.util.Arrays.asList(null, "ready").iterator();
            var time = new FakeTime(0);

            String result = await(observations::next).usingTime(time, time)
                    .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(3))
                    .until(check);

            assertEquals("ready", result);
            assertFalse(observations.hasNext());
        }
    }

    @Test
    void unrelatedTypeChecksRemainOrdinaryMismatches() {
        for (var check : List.<Condition.Typed<? super String, ?>>of(Conditions.instanceOf(Integer.class), Conditions.exactlyInstanceOf(Integer.class))) {
            var time = new FakeTime(0);
            var waiting = await(() -> "actual").usingTime(time, time)
                    .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(2));
            var captured = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(check));
            assertEquals(AwaitFailure.Reason.TIMEOUT, captured.failure().reason());
            assertThrows(AwaitAssertionError.class, () -> waiting.until(check));
        }
        String actual = "actual";
        assertSame(actual, await(() -> actual).until(Conditions.notEqualTo("different")));
        assertSame(actual, await(() -> actual).until(Conditions.notSameAs("different")));
        assertSame(actual, await(() -> actual).until(Conditions.notIn(List.of("one", "two"))));
    }

    @Test
    void objectConditionsPreserveAndNarrowTypes() {
        var pollingTime = new FakeTime(0);
        var actual = new Child();
        Callable<Parent> source = () -> actual;
        var waiting = await(source).usingTime(pollingTime, pollingTime);

        assertSame(actual, waiting.until(Conditions.sameAs(actual)));
        assertSame(actual, waiting.until(Conditions.notSameAs(new Child())));
        assertSame(actual, waiting.until(Conditions.in(List.of(actual, new Parent()))));
        assertSame(actual, waiting.until(Conditions.notIn(List.of(new Parent()))));
        assertSame(actual, waiting.until(Conditions.matches(value -> value instanceof Child)));

        Child child = waiting.until(Conditions.instanceOf(Child.class));
        Child exact = waiting.until(Conditions.exactlyInstanceOf(Child.class));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertEquals(Unsatisfied.class, evaluate(
                Conditions.exactlyInstanceOf(Parent.class), actual).getClass());
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        var pollingTime = new FakeTime(0);
        java.util.concurrent.Callable<java.util.Optional<Object>> source = () -> Optional.of("ready");
        var waiting = await(source::call).usingTime(pollingTime, pollingTime);

        Object expected = waiting.until(OptionalConditions.hasValue(Conditions.equalTo((Object) "ready")));
        Object selected = waiting.until(OptionalConditions.hasValue(Conditions.matches(value -> value.toString().startsWith("r"))));
        String narrowed = waiting.until(OptionalConditions.hasValue(instanceOf(String.class)));
        String nested = await(() -> Optional.of("ready")).usingTime(pollingTime, pollingTime).until(OptionalConditions.hasValue(StringConditions.startsWith("rea")));
        Integer transformed = await(() -> Optional.of("ready").map(String::length)).usingTime(pollingTime, pollingTime).until(OptionalConditions.present);

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }

    private static <T> ValueAwait<T> stage(
            FakeTime time, Callable<T> source) {
        return timedAwait(source, DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(3)), time, time);
    }
    private static class Parent {
    }

    private static final class Child extends Parent {
    }
}
