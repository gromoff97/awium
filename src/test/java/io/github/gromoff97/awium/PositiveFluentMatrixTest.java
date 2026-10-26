package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Await.CollectionViewSource;
import io.github.gromoff97.awium.Await.MapViewSource;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Conditions.await;
import static io.github.gromoff97.awium.CollectionConditions.await;
import static io.github.gromoff97.awium.OptionalConditions.await;
import static io.github.gromoff97.awium.MapConditions.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class PositiveFluentMatrixTest {

    private static final Duration EVERY = ofMillis(1);
    private static final Duration UP_TO = ofSeconds(1);

    @Test
    void objectFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new Object();
        Callable<Object> source = () -> actual;

        Object selected = await(source).usingTime(pollingTime, pollingTime)
                .every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull.because("object full chain"));
        assertSame(actual, selected);

        PreservingCondition<Object> selecting = condition(
                "select actual", ConditionResult::satisfied);
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting.because("selected object")));
        String nil = await(() -> (String) null).usingTime(pollingTime, pollingTime).until(isNull.because("value is absent"));
        assertSame(null, nil);
    }

    @Test
    void optionalFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var value = new String("value");
        java.util.concurrent.Callable<java.util.Optional<String>> source = () -> Optional.of(value);

        String selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(present.because("optional full chain"));
        Optional<String> empty = Optional.empty();
        Optional<String> absentValue = await(() -> empty).usingTime(pollingTime, pollingTime).until(absent.because("optional is empty"));

        assertSame(value, selected);
        assertSame(empty, absentValue);
    }

    @Test
    void collectionFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new ArrayList<>(List.of("value"));
        java.util.concurrent.Callable<ArrayList<String>> source = () -> actual;

        Condition.PreservingCondition<Collection<?>> collectionCondition = nonEmpty;
        Condition.PreservingCondition<Collection<?>> explained =
                collectionCondition.because("collection full chain");
        ArrayList<String> raw = await(source).usingTime(pollingTime, pollingTime).until(collectionCondition);
        ArrayList<String> selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(explained);

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void mapFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(java.util.Map.of("key", "value"));
        java.util.concurrent.Callable<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> raw = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.nonEmpty);
        LinkedHashMap<String, String> selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(MapConditions.nonEmpty.because("map full chain"));

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void wildcardLambdasPreserveSelectionBoundsWithoutTypeArguments() {
        Number number = Integer.valueOf(1000);
        CharSequence text = new StringBuilder("ready");
        List<? extends Number> values = List.of(number);
        Map<? extends Number, ? extends CharSequence> index = Map.of(number, text);

        var element = await(() -> values).until(single.because("one covariant element"));
        var entry = await(() -> index).until(MapConditions.singleEntry.because("one covariant entry"));
        var original = await(() -> values).until(nonEmpty.because("original covariant list"));
        var result = await(() -> values).tryUntil(single.because("covariant result"));

        assertEquals(1000, element.intValue());
        assertEquals(1000, entry.getKey().intValue());
        assertEquals(5, entry.getValue().length());
        assertEquals(1000, original.getFirst().intValue());
        assertSame(number, element);
        assertSame(number, entry.getKey());
        assertSame(text, entry.getValue());
        assertSame(values, original);
        assertEquals(1000, switch (result) {
            case AwaitResult.Satisfied(var attempts, var selected) -> selected.intValue();
            case AwaitResult.Failed(var attempts, var failure) -> throw new AssertionError(failure);
        });
    }

    @Test
    void wildcardViewsPreserveContainersAndSelectOriginalElementsOncePerAttempt() {
        var time = new FakeTime(0);
        Number element = Integer.valueOf(1000);
        List<? extends Number> values = new ArrayList<>(List.of(element));
        var entries = new LinkedHashMap<Number, CharSequence>();
        entries.put(element, new StringBuilder("ready"));
        Map<? extends Number, ? extends CharSequence> map = entries;
        var entry = entries.entrySet().iterator().next();
        int[] collectionCalls = {0};
        int[] mapCalls = {0};
        var collectionSource = new CollectionViewSource<Number, List<? extends Number>>(() -> {
            collectionCalls[0]++;
            return values;
        });
        var mapSource = new MapViewSource<Number, CharSequence,
                Map<? extends Number, ? extends CharSequence>>(() -> {
                    mapCalls[0]++;
                    return map;
                });

        assertSame(values, await(collectionSource).usingTime(time, time).until(nonEmpty));
        List<? extends Number> compared = await(collectionSource).usingTime(time, time)
                .until(equalTo(values).because("the original covariant list"));
        assertSame(values, compared);
        var selected = await(collectionSource).usingTime(time, time).until(single.because("covariant element"));
        assertEquals(1000, selected.intValue());
        assertSame(element, selected);
        assertSame(element, assertInstanceOf(AwaitResult.Satisfied.class,
                await(collectionSource).usingTime(time, time).tryUntil(single)).result());
        assertSame(map, await(mapSource).usingTime(time, time).until(MapConditions.nonEmpty));
        Map<? extends Number, ? extends CharSequence> comparedMap = await(mapSource).usingTime(time, time)
                .until(equalTo(map).because("the original covariant map"));
        assertSame(map, comparedMap);
        var selectedEntry = await(mapSource).usingTime(time, time).until(MapConditions.singleEntry.because("covariant entry"));
        assertEquals(1000, selectedEntry.getKey().intValue());
        assertEquals(5, selectedEntry.getValue().length());
        assertSame(entry, selectedEntry);
        assertSame(entry, assertInstanceOf(AwaitResult.Satisfied.class,
                await(mapSource).usingTime(time, time).tryUntil(MapConditions.singleEntry)).result());
        assertEquals(4, collectionCalls[0]);
        assertEquals(4, mapCalls[0]);
    }

    @Test
    void jdkVariablesAndMethodReferencesHaveTheSameSelectionTypes() {
        Callable<Optional<String>> optional = () -> Optional.of("ready");
        Callable<ArrayList<String>> collection = () -> new ArrayList<>(List.of("ready"));
        Callable<LinkedHashMap<String, Integer>> map = () -> new LinkedHashMap<>(Map.of("ready", 1));

        String optionalValue = await(optional::call).until(present.because("required value"));
        String element = await(collection::call).until(single.because("unique value"));
        Map.Entry<String, Integer> entry = await(map::call).until(MapConditions.singleEntry.because("unique entry"));
        AwaitResult<Optional<String>, String> attempt = await(optional::call).tryUntil(present);

        assertEquals("ready", optionalValue);
        assertEquals("ready", element);
        assertEquals(Map.entry("ready", 1), entry);
        assertEquals(optionalValue, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(optionalValue, await(optional).until(present));
        assertEquals(element, await(collection).until(single));
        assertEquals(entry, await(map).until(MapConditions.singleEntry));
        var time = new FakeTime(0);
        List<String> stages = await(collection).usingTime(time, time).every(ofNanos(1)).until(first, last);
        assertEquals(List.of("ready", "ready"), stages);
    }

    @Test
    void explicitCatalogFactoriesAcceptOverloadedConstructors() {
        StringBuilder text = Conditions.await(StringBuilder::new).until(StringConditions.empty);
        ArrayList<String> values = CollectionConditions.await(ArrayList<String>::new).until(CollectionConditions.empty);
        LinkedHashMap<String, Integer> entries = MapConditions.await(LinkedHashMap<String, Integer>::new).until(MapConditions.empty);
        Optional<?> absent = OptionalConditions.await(Optional::empty).until(OptionalConditions.absent);
        assertEquals(0, text.length());
        assertEquals(0, values.size());
        assertEquals(0, entries.size());
        assertEquals(Optional.empty(), absent);
    }

    @Test
    void diagnosticTerminalsRetainSpecializedSourceAndResultTypes() {
        java.util.concurrent.Callable<java.util.Optional<String>> optional = () -> Optional.of("ready");
        java.util.concurrent.Callable<List<String>> collection = () -> List.of("ready");
        java.util.concurrent.Callable<Map<String, Integer>> map = () -> Map.of("ready", 1);

        AwaitResult<Optional<String>, String> optionalResult = await(optional).tryUntil(present);
        AwaitResult<List<String>, String> collectionResult = await(collection).tryUntil(single);
        AwaitResult<List<String>, String> firstResult = await(collection).tryUntil(first);
        AwaitResult<List<String>, String> lastResult = await(collection).tryUntil(last);
        AwaitResult<Map<String, Integer>, Map.Entry<String, Integer>> mapResult = await(map).tryUntil(MapConditions.singleEntry);

        for (var result : List.of(optionalResult, collectionResult, firstResult, lastResult)) {
            assertEquals("ready", assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        }
        assertEquals(Map.entry("ready", 1), assertInstanceOf(AwaitResult.Satisfied.class, mapResult).result());
    }

    @Test
    void callbackConditionsComposeWithJdkFunctions() {
        Predicate<String> nonNull = value -> value != null;
        Predicate<String> text = nonNull.and(value -> !value.isBlank());
        Function<String, String> trim = String::trim;
        var calls = new ArrayList<String>();
        Consumer<String> first = value -> calls.add("first " + value);
        BiPredicate<String, Integer> entry = (key, value) -> value > 0;

        String matched = await(() -> "ready").until(matches(Predicate.not(text).negate()::test));
        String transformed = await(() -> trim.apply(" ready ")).until(isNotNull);
        String custom = await(() -> trim.apply(" ready ")).until(condition("ready", ConditionResult::satisfied));
        String asserted = await(() -> "ready").until(asserted(first.andThen(value -> calls.add("second " + value))::accept));
        Map<String, Integer> matchingEntry = await(() -> Map.of("ready", 1))
                .until(MapConditions.anyEntry(entry.and((key, value) -> !key.isBlank())::test));

        assertEquals(List.of("ready", "ready", "ready", "ready"), List.of(matched, transformed, custom, asserted));
        assertEquals(List.of("first ready", "second ready"), calls);
        assertEquals(Map.of("ready", 1), matchingEntry);
    }

    @Test
    void optionalValuesCanThemselvesBeClassesConditionsOrPredicates() {
        Class<?> expectedClass = String.class;
        PreservingCondition<String> expectedCondition = matches(String::isBlank);
        Predicate<String> expectedPredicate = String::isBlank;

        Optional<Class<?>> actualClass = Optional.of(expectedClass);
        Class<?> type = await(() -> actualClass).until(hasValue(equalTo(expectedClass)));
        PreservingCondition<String> condition = await(() -> Optional.of(expectedCondition)).until(hasValue(equalTo(expectedCondition)));
        Predicate<String> predicate = await(() -> Optional.of(expectedPredicate)).until(hasValue(equalTo(expectedPredicate)));

        assertSame(expectedClass, type);
        assertSame(expectedCondition, condition);
        assertSame(expectedPredicate, predicate);
    }

    @Test
    void evaluatorFactoriesPreserveObservationsInSingleAndOrderedWaits() {
        Callable<CheckedFunction<CharSequence, ConditionResult<CharSequence>>> broadFactory = () -> ignored -> ConditionResult.satisfied("replacement");
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time);

        String direct = waiting.until(condition("direct", ConditionResult::satisfied));
        String created = waiting.until(conditionPerWait("created", PositiveFluentMatrixTest::evaluator).because("ready"));
        String preserved = waiting.until(conditionPerWait("preserved", PositiveFluentMatrixTest::evaluator));
        String concrete = waiting.until(conditionPerWait("broad", broadFactory));
        List<String> captured = waiting.until(conditionPerWait("first", PositiveFluentMatrixTest::evaluator),
                conditionPerWait("second", PositiveFluentMatrixTest::evaluator));

        assertEquals(List.of("ready", "ready", "ready", "ready"), List.of(direct, created, preserved, concrete));
        assertEquals(List.of("ready", "ready"), captured);
    }

    private static CheckedFunction<String, ConditionResult<String>> evaluator() {
        return ConditionResult::satisfied;
    }

}
