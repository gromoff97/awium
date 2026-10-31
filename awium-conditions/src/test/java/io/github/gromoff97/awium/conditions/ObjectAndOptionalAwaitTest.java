package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.conditions.OptionalConditions;
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
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.evaluate;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.rejection;
import static io.github.gromoff97.awium.conditions.Conditions.*;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.*;
import static io.github.gromoff97.awium.conditions.StringConditions.startsWith;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
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
        var waiting = await(() -> actual);
        var value = waiting.until(in(singletonList(actual.clone())));
        AwaitResult<String[], String[]> captured = tryAwait(() -> actual).until(in(singletonList(actual.clone())));
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

            var waiting = await(() -> actual).every(ofNanos(1));
            String excluded = waiting.until(notIn(List.of()));
            assertSame(actual, excluded);
            AwaitResult<String, String> captured = tryAwait(() -> actual).every(ofNanos(1)).until(notIn(List.of()));
            assertSame(actual, captured.orThrow());

            var check = in(List.<String>of());
            rejection(check, actual);
            assertEquals(List.of(), check.references().getFirst().value());
        }
    }

    @Test
    void comparisonsPreserveTheObservedTypeAndNarrowingCanFailAtRuntime() {
        var waiting = await(() -> "ready");
        for (var condition : List.of(equalTo("different"), sameAs("different"), in(List.of("one", "two")))) {
            rejection(condition, "ready");
        }
        String unequal = waiting.until(notEqualTo("different"));
        String different = waiting.until(notSameAs("different"));
        String excluded = waiting.until(notIn(List.of("one", "two")));
        rejection(instanceOf(Integer.class), "ready");
        rejection(hasValue(instanceOf(Integer.class)), Optional.of("ready"));
        Number broadened = await(() -> 1).until(instanceOf(Number.class));

        assertEquals(List.of("ready", "ready", "ready"), List.of(unequal, different, excluded));
        assertEquals(1, broadened);
    }

    @Test
    void matchingConditionsRetryAndReturnTheExactSuccessfulObservationInBothTerminals() {
        var first = new Object();
        var ready = new Object();
        var observations = new Object[]{first, ready, first, ready};
        int[] calls = {0};
        var waiting = await(() -> observations[calls[0]++]).every(ofNanos(2));

        assertSame(ready, waiting.until(matches(actual -> actual == ready)));
        var result = tryAwait(() -> observations[calls[0]++]).every(ofNanos(2)).until(matches(ready::equals));

        assertSame(ready, result.orThrow());
        assertEquals(2, attempts(result).size());
        assertEquals(4, calls[0]);

    }

    @Test
    void broadPreservingConditionsRetainTheConcreteObservationInBothTerminals() {
        var actual = new String("observed");
        var waiting = await(() -> actual);
        PreservingCondition<Object> condition = asserted(ignored -> {});

        String value = waiting.until(condition);
        AwaitResult<String, String> result = tryAwait(() -> actual).until(condition);

        assertSame(actual, value);
        assertSame(actual, result.orThrow());
    }

    @Test
    void nullablePreservingTerminalsReturnNullOnSuccess() {
        assertNull(await((Callable<Object>) () -> null).until(isNull));
        assertNull(await((Callable<String>) () -> null).until(matches(actual -> actual == null)));
        assertNull(await((Callable<String>) () -> null).until(condition("nullable property", ignored -> satisfied(ignored))));
    }

    @Test
    void capturesNullThenPresentWithTheObservedType() {
        String[] observations = {null, "ready", null, "ready"};
        int[] reads = {0};
        var waiting = await(() -> observations[reads[0]++]).every(ofNanos(1));

        List<String> values = waiting.until(captured(isNull, isNotNull));
        AwaitResult<String, List<String>> captured = tryAwait(() -> observations[reads[0]++]).every(ofNanos(1)).until(captured(
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

        String missing = await(() -> items)
                .until(singleElement(isNull));
        Optional<String> nested = await(() -> Optional.of(empty))
                .until(hasValue(absent));
        Optional<String> mapped = await(() -> Map.of("key", empty))
                .until(valueFor("key", absent));

        assertNull(missing);
        assertSame(empty, nested);
        assertSame(empty, mapped);
    }

    @Test
    void optionalValueConditionsReturnTheContainedValueThroughUntil() {
        var equalValue = new Object();

        assertSame(equalValue, await(() -> Optional.of(equalValue))
                .until(hasValue(equalTo(equalValue))));
    }

    @Test
    void narrowingWaitsForANullSourceToProduceAValue() {
        for (var check : List.of(instanceOf(String.class), exactlyInstanceOf(String.class))) {
            var observations = asList(null, "ready").iterator();

            String result = await(observations::next)
                    .every(java.time.Duration.ofNanos(1))
                    .until(check);

            assertEquals("ready", result);
            assertFalse(observations.hasNext());
        }
    }

    @Test
    void unrelatedTypeChecksRemainOrdinaryMismatches() {
        for (var check : List.<Condition<? super String, ?>>of(instanceOf(Integer.class), exactlyInstanceOf(Integer.class))) {

            var mismatch = rejection(check, "actual");
            assertThat(mismatch.mismatch()).contains("java.lang.String");
            assertThat(check.description()).contains("java.lang.Integer");
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
        var waiting = await(source);

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
        var waiting = await(source::call);

        Object expected = waiting.until(hasValue(equalTo((Object) "ready")));
        Object selected = waiting.until(hasValue(matches(value -> value.toString().startsWith("r"))));
        String narrowed = waiting.until(hasValue(instanceOf(String.class)));
        String nested = await(() -> Optional.of("ready")).until(hasValue(startsWith("rea")));
        Integer transformed = await(() -> Optional.of("ready").map(String::length)).until(OptionalConditions.present);

        assertEquals("ready", expected);
        assertEquals("ready", selected);
        assertEquals("ready", narrowed);
        assertEquals("ready", nested);
        assertEquals(5, transformed);
    }
    private static class Parent {
    }

    private static final class Child extends Parent {
    }
}
