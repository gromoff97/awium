package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.StringConditions.startsWith;
import static io.github.gromoff97.awium.internal.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.internal.WaitConfiguration.DEFAULT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Named.named;

class ObjectAndOptionalAwaitTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void readyCandidateCollectionsPreserveScalarAndContainerTypes() {
        var text = await(() -> "ready").until(in(List.of("pending", "ready")));
        var list = List.of("ready");
        var map = Map.of("ready", 1);
        var optional = Optional.of("ready");
        var selectedList = await(() -> list).until(in(List.of(List.of("ready"))));
        var selectedMap = await(() -> map).until(in(List.of(Map.of("ready", 1))));
        var selectedOptional = await(() -> optional).until(in(List.of(Optional.of("ready"))));
        AwaitResult<Map<String, Integer>, Map<String, Integer>> captured = tryAwait(() -> map).until(
                notIn(List.of(Map.of("failed", 0))));

        assertEquals(5, text.length());
        assertSame(list, selectedList);
        assertEquals(5, selectedList.getFirst().length());
        assertSame(map, selectedMap);
        assertEquals(1, selectedMap.get("ready").intValue());
        assertSame(optional, selectedOptional);
        assertEquals(5, selectedOptional.orElseThrow().length());
        assertSame(map, captured.orThrow());
    }

    @ParameterizedTest
    @MethodSource("arrayCandidates")
    void stringArrayCandidatesRemainWholeValuesInBothTerminalsAndSequences(String[] actual) {
        var waiting = await(() -> actual).usingTime(time, time);
        var value = waiting.until(in(singletonList(actual.clone())));
        AwaitResult<String[], String[]> captured = diagnostic(waiting).until(in(singletonList(actual.clone())));
        var values = waiting.until(captured(in(singletonList(actual.clone())), notIn(singletonList(new String[]{"failed"}))));

        assertSame(actual, value);
        assertEquals(actual.length, value.length);
        assertSame(actual, captured.orThrow());
        assertSame(actual, values.getFirst());
        assertEquals(actual.length, values.getLast().length);
        for (var element : value) assertEquals(5, element.length());
    }

    private static List<Named<String[]>> arrayCandidates() {
        return List.of(named("empty", new String[]{}), named("ready", new String[]{"ready"}));
    }

    @Test
    void primitiveArrayCandidatesKeepTheArrayTypeAndIdentity() {
        int[] actual = {1, 2};
        var selected = await(() -> actual).until(in(singletonList(actual.clone())));
        assertSame(actual, selected);
        assertEquals(1, selected[0]);
    }

    @Test
    void candidateCollectionsMayBeEmptyButNotNull() {
        assertThrows(NullPointerException.class, () -> in(null));
        assertThrows(NullPointerException.class, () -> notIn(null));
        for (String actual : asList(null, "", "ready")) {
            var time = new FakeTime(0);
            var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
            String excluded = waiting.until(notIn(List.of()));
            assertSame(actual, excluded);
            AwaitResult<String, String> captured = diagnostic(waiting).until(notIn(List.of()));
            assertSame(actual, captured.orThrow());

            var failed = diagnostic(waiting).until(in(List.of()));
            failure(TIMEOUT, failed);
            assertEquals(List.of(), failed.conditions().getLast().condition().references().getFirst().value());
            assertFailure(TIMEOUT, () -> waiting.until(in(List.of())));
        }
    }

    @Test
    void comparisonsPreserveTheObservedTypeAndNarrowingCanFailAtRuntime() {
        var waiting = await(() -> "ready").usingTime(time, time).upTo(ofNanos(1));
        for (var condition : List.of(equalTo("different"), sameAs("different"), in(List.of("one", "two")))) {
            AwaitResult<String, String> result = diagnostic(waiting).until(condition);
            failure(TIMEOUT, result);
        }
        String unequal = waiting.until(notEqualTo("different"));
        String different = waiting.until(notSameAs("different"));
        String excluded = waiting.until(notIn(List.of("one", "two")));
        AwaitResult<String, Integer> narrowed = diagnostic(waiting).until(instanceOf(Integer.class));
        AwaitResult<Optional<String>, Integer> nested = tryAwait(() -> Optional.of("ready"))
                .usingTime(time, time).upTo(ofNanos(1)).until(hasValue(instanceOf(Integer.class)));
        Number broadened = await(() -> 1).until(instanceOf(Number.class));

        assertEquals(List.of("ready", "ready", "ready"), List.of(unequal, different, excluded));
        failure(TIMEOUT, narrowed);
        failure(TIMEOUT, nested);
        assertEquals(1, broadened);
    }

    @Test
    void matchingConditionsRetryAndReturnTheExactSuccessfulObservationInBothTerminals() {
        var first = new Object();
        var ready = new Object();
        var observations = new Object[]{first, ready, first, ready};
        int[] calls = {0};
        var waiting = await(() -> observations[calls[0]++]).usingTime(time, time).every(ofNanos(2)).upTo(ofNanos(5));

        assertSame(ready, waiting.until(matches(actual -> actual == ready)));
        var result = diagnostic(waiting).until(matches(ready::equals));

        assertSame(ready, result.orThrow());
        assertEquals(2, attempts(result).size());
        assertEquals(4, calls[0]);
        assertThat(time.parkRequests).containsExactly(2L, 2L);
    }

    @Test
    void broadPreservingConditionsRetainTheConcreteObservationInBothTerminals() {
        var actual = new String("observed");
        var waiting = await(() -> actual).usingTime(time, time);
        PreservingCondition<Object> condition = asserted(ignored -> {});

        String value = waiting.until(condition);
        AwaitResult<String, String> result = diagnostic(waiting).until(condition);

        assertSame(actual, value);
        assertSame(actual, result.orThrow());
    }

    @Test
    void nullablePreservingTerminalsReturnNullOnSuccess() {
        assertNull(await((Callable<Object>) () -> null).usingTime(time, time).until(isNull));
        assertNull(await((Callable<String>) () -> null).usingTime(time, time).until(matches(actual -> actual == null)));
        assertNull(await((Callable<String>) () -> null).usingTime(time, time).until(condition("nullable property", ignored -> satisfied(ignored))));
    }

    @Test
    void capturesNullThenPresentWithTheObservedType() {
        String[] observations = {null, "ready", null, "ready"};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++]).usingTime(time, time).every(ofNanos(1));

        List<String> values = waiting.until(captured(isNull, isNotNull));
        AwaitResult<String, List<String>> captured = diagnostic(waiting).until(captured(
                isNull, isNotNull));

        assertNull(values.getFirst());
        assertEquals("ready", values.getLast());
        assertEquals(values, captured.orThrow());
        assertEquals(2, attempts(captured).size());
        assertEquals(4, reads[0]);
    }

    @Test
    void nestedAbsenceChecksPreserveTheSelectedTypes() {
        var items = singletonList((String) null);
        Optional<String> empty = Optional.empty();

        String missing = await(() -> items).usingTime(time, time).upTo(ofNanos(1))
                .until(singleElement(isNull));
        Optional<String> nested = await(() -> Optional.of(empty)).usingTime(time, time).upTo(ofNanos(1))
                .until(hasValue(absent));
        Optional<String> mapped = await(() -> Map.of("key", empty)).usingTime(time, time).upTo(ofNanos(1))
                .until(valueFor("key", absent));

        assertNull(missing);
        assertSame(empty, nested);
        assertSame(empty, mapped);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();

        assertSame(equalValue, await(() -> Optional.of(equalValue)).usingTime(time, time)
                .until(hasValue(equalTo(equalValue))));
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        int[] calls = {0};
        ValueAwait<Integer> stage = stage(time, () -> ++calls[0]);
        PreservingCondition<Integer> evenObservation = matches(value -> value % 2 == 0);

        assertEquals(2, stage.until(evenObservation));
        assertEquals(4, stage.until(evenObservation));
        assertEquals(4, calls[0]);
    }

    @Test
    void reusableStageStartsFreshAfterControlledAndUncontrolledFailures() {
        int[] sourceCalls = {0};
        ValueAwait<String> stage = stage(time, () -> {
            sourceCalls[0]++;
            return "value";
        });
        Condition.Typed<String, String> never = condition(
                "never", value -> unsatisfied("not yet"));
        var failure = new IllegalStateException("condition failed");
        Condition.Typed<String, String> broken = condition(
                "broken", value -> {
                    throw failure;
                });

        assertFailure(TIMEOUT, () -> stage.until(never));
        assertSame(failure, assertFailure(CONDITION_FAILED,
                () -> stage.until(broken)).getCause());

        assertEquals("value", stage.until(condition(
                "ready", ignored -> satisfied(ignored))));
        assertEquals(5, sourceCalls[0]);
    }

    @Test
    void narrowingWaitsForANullSourceToProduceAValue() {
        for (var check : List.of(instanceOf(String.class), exactlyInstanceOf(String.class))) {
            var observations = asList(null, "ready").iterator();
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
        for (var check : List.<Typed<? super String, ?>>of(instanceOf(Integer.class), exactlyInstanceOf(Integer.class))) {
            var time = new FakeTime(0);
            var waiting = await(() -> "actual").usingTime(time, time)
                    .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(2));
            var captured = assertInstanceOf(Failed.class, diagnostic(waiting).until(check));
            assertEquals(TIMEOUT, captured.failure().reason());
            assertThat(captured.failure().message()).contains("java.lang.Integer", "java.lang.String");
            assertThrows(AwaitAssertionError.class, () -> waiting.until(check));
        }
        String actual = "actual";
        assertSame(actual, await(() -> actual).until(notEqualTo("different")));
        assertSame(actual, await(() -> actual).until(notSameAs("different")));
        assertSame(actual, await(() -> actual).until(notIn(List.of("one", "two"))));
    }

    @Test
    void objectConditionsPreserveAndNarrowTypes() {
        var actual = new Child();
        Callable<Parent> source = () -> actual;
        var waiting = await(source).usingTime(time, time);

        assertSame(actual, waiting.until(sameAs(actual)));
        assertSame(actual, waiting.until(notSameAs(new Child())));
        assertSame(actual, waiting.until(in(List.of(actual, new Parent()))));
        assertSame(actual, waiting.until(notIn(List.of(new Parent()))));
        assertSame(actual, waiting.until(matches(value -> value instanceof Child)));

        Child child = waiting.until(instanceOf(Child.class));
        Child exact = waiting.until(exactlyInstanceOf(Child.class));

        assertSame(actual, child);
        assertSame(actual, exact);
        assertInstanceOf(Unsatisfied.class, evaluate(
                exactlyInstanceOf(Parent.class), actual));
    }

    @Test
    void optionalOverloadsReturnValuesAndNarrowedTypes() {
        Callable<Optional<Object>> source = () -> Optional.of("ready");
        var waiting = await(source::call).usingTime(time, time);

        Object expected = waiting.until(hasValue(equalTo((Object) "ready")));
        Object selected = waiting.until(hasValue(matches(value -> value.toString().startsWith("r"))));
        String narrowed = waiting.until(hasValue(instanceOf(String.class)));
        String nested = await(() -> Optional.of("ready")).usingTime(time, time).until(hasValue(startsWith("rea")));
        Integer transformed = await(() -> Optional.of("ready").map(String::length)).usingTime(time, time).until(OptionalConditions.present);

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }

    private static <T> ValueAwait<T> stage(FakeTime time, Callable<T> source) {
        return timedAwait(source, DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(3)), time, time);
    }
    private static class Parent {
    }

    private static final class Child extends Parent {
    }
}
