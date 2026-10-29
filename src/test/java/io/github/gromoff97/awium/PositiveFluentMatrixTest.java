package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Await.CollectionViewSource;
import io.github.gromoff97.awium.Await.MapViewSource;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.result.AwaitResult;
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

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull);
        assertSame(actual, selected);

        PreservingCondition<Object> selecting = condition(
                "select actual", ignored -> ConditionResult.satisfied());
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting));
        String nil = await(() -> (String) null).usingTime(pollingTime, pollingTime).until(isNull);
        assertSame(null, nil);
    }

    @Test
    void optionalFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var value = new String("value");
        java.util.concurrent.Callable<java.util.Optional<String>> source = () -> Optional.of(value);

        String selected = await(source::call).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(present);
        Optional<String> empty = Optional.empty();
        Optional<String> absentValue = await(() -> empty).usingTime(pollingTime, pollingTime).until(absent);

        assertSame(value, selected);
        assertSame(empty, absentValue);
    }

    @Test
    void collectionFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new ArrayList<>(List.of("value"));
        java.util.concurrent.Callable<ArrayList<String>> source = () -> actual;

        Condition.PreservingCondition<Collection<?>> collectionCondition = nonEmpty;
        ArrayList<String> raw = await(source::call).usingTime(pollingTime, pollingTime).until(collectionCondition);
        ArrayList<String> selected = await(source::call).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(collectionCondition);

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void mapFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new LinkedHashMap<>(java.util.Map.of("key", "value"));
        java.util.concurrent.Callable<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> raw = await(source::call).usingTime(pollingTime, pollingTime).until(MapConditions.nonEmpty);
        LinkedHashMap<String, String> selected = await(source::call).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(MapConditions.nonEmpty);

        assertSame(actual, raw);
        assertSame(actual, selected);
    }

    @Test
    void wildcardLambdasPreserveSelectionBoundsWithoutTypeArguments() {
        Number number = Integer.valueOf(1000);
        CharSequence text = new StringBuilder("ready");
        List<? extends Number> values = List.of(number);
        Map<? extends Number, ? extends CharSequence> index = Map.of(number, text);

        var element = await(() -> values).until(singleElement);
        var entry = await(() -> index).until(MapConditions.singleEntry);
        var original = await(() -> values).until(nonEmpty);
        var result = tryAwait(() -> values).until(singleElement);

        assertEquals(1000, element.intValue());
        assertEquals(1000, entry.getKey().intValue());
        assertEquals(5, entry.getValue().length());
        assertEquals(1000, original.getFirst().intValue());
        assertSame(number, element);
        assertSame(number, entry.getKey());
        assertSame(text, entry.getValue());
        assertSame(values, original);
        assertEquals(1000, switch (result) {
            case AwaitResult.Satisfied(var history, var selected) -> selected.intValue();
            case AwaitResult.Failed(var history, var failure) -> throw new AssertionError(failure);
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
                .until(equalTo(values));
        assertSame(values, compared);
        var selected = await(collectionSource).usingTime(time, time).until(singleElement);
        assertEquals(1000, selected.intValue());
        assertSame(element, selected);
        assertSame(element, tryAwait(collectionSource).usingTime(time, time).until(singleElement).orThrow());
        assertSame(map, await(mapSource).usingTime(time, time).until(MapConditions.nonEmpty));
        Map<? extends Number, ? extends CharSequence> comparedMap = await(mapSource).usingTime(time, time)
                .until(equalTo(map));
        assertSame(map, comparedMap);
        var selectedEntry = await(mapSource).usingTime(time, time).until(MapConditions.singleEntry);
        assertEquals(1000, selectedEntry.getKey().intValue());
        assertEquals(5, selectedEntry.getValue().length());
        assertSame(entry, selectedEntry);
        assertSame(entry, tryAwait(mapSource).usingTime(time, time).until(MapConditions.singleEntry).orThrow());
        assertEquals(4, collectionCalls[0]);
        assertEquals(4, mapCalls[0]);
    }

    @Test
    void covariantCollectionSourcesUseTheSamePredicateSelectionDirectlyAndInCaptured() {
        Number expected = new java.math.BigDecimal("42");
        Number actual = new java.math.BigDecimal("42");
        List<? extends Number> values = List.of(actual);
        int[] calls = {0};
        var source = new CollectionViewSource<Number, List<? extends Number>>(
                () -> ++calls[0] == 1 ? List.of(0) : values);
        var time = new FakeTime(0);
        var waiting = await(source).usingTime(time, time).every(ofNanos(1));
        var check = singleElement(expected::equals).because("the expected number must arrive");

        var selected = waiting.until(check);
        assertEquals(42, selected.intValue());
        assertSame(actual, selected);
        assertEquals(2, calls[0]);
        var result = diagnostic(waiting).until(check);
        assertEquals(42, result.orThrow().intValue());
        assertSame(actual, result.orThrow());
        assertSame(values, result.attempts().getFirst().observed());
        assertEquals(3, calls[0]);
        var observations = waiting.until(captured(nonEmpty, check));
        assertEquals(42, observations.getLast().getFirst().intValue());
        assertSame(values, observations.getFirst());
        assertSame(values, observations.getLast());
        assertEquals(5, calls[0]);
    }

    @Test
    void covariantMapSourcesUseTheSamePredicateSelectionDirectlyAndInCaptured() {
        Number expected = new java.math.BigDecimal("42");
        Number actual = new java.math.BigDecimal("42");
        Map<String, ? extends Number> values = Map.of("answer", actual);
        var source = new MapViewSource<String, Number, Map<String, ? extends Number>>(() -> values);
        var waiting = await(source);
        var check = MapConditions.valueFor("answer", expected::equals).because("the answer must arrive");

        var selected = waiting.until(check);
        assertEquals(42, selected.intValue());
        assertSame(actual, selected);
        var result = diagnostic(waiting).until(check);
        assertEquals(42, result.orThrow().intValue());
        assertSame(actual, result.orThrow());
        assertSame(values, result.attempts().getFirst().observed());
        var time = new FakeTime(0);
        var observations = diagnostic(waiting.usingTime(time, time).every(ofNanos(1)))
                .until(captured(MapConditions.nonEmpty, check)).orThrow();
        assertEquals(42, observations.getLast().get("answer").intValue());
        assertSame(values, observations.getFirst());
        assertSame(values, observations.getLast());
    }

    @Test
    void callableMethodReferencesRetainSelectionTypes() {
        Callable<Optional<String>> optional = () -> Optional.of("ready");
        Callable<ArrayList<String>> collection = () -> new ArrayList<>(List.of("ready"));
        Callable<LinkedHashMap<String, Integer>> map = () -> new LinkedHashMap<>(Map.of("ready", 1));

        String optionalValue = await(optional::call).until(present);
        String element = await(collection::call).until(singleElement);
        Map.Entry<String, Integer> entry = await(map::call).until(MapConditions.singleEntry);
        AwaitResult<Optional<String>, String> attempt = tryAwait(optional::call).until(present);

        assertEquals("ready", optionalValue);
        assertEquals("ready", element);
        assertEquals(Map.entry("ready", 1), entry);
        assertEquals(optionalValue, attempt.orThrow());
        assertEquals(optionalValue, await(() -> optional.call()).until(present));
        assertEquals(element, await(() -> collection.call()).until(singleElement));
        assertEquals(entry, await(() -> map.call()).until(MapConditions.singleEntry));
        var time = new FakeTime(0);
        List<ArrayList<String>> stages = await(collection::call).usingTime(time, time).every(ofNanos(1)).until(captured(firstElement, lastElement));
        assertEquals(List.of(List.of("ready"), List.of("ready")), stages);
    }

    @Test
    void covariantCallableMethodReferencesRetainSelectionTypes() {
        var text = new StringBuilder("ready");
        var values = new ArrayList<>(List.of("ready"));
        var entries = new LinkedHashMap<>(Map.of("ready", 1));
        Callable<? extends StringBuilder> scalar = () -> text;
        Callable<? extends Optional<String>> optional = () -> Optional.of("ready");
        Callable<? extends ArrayList<String>> collection = () -> values;
        Callable<? extends LinkedHashMap<String, Integer>> map = () -> entries;

        var actual = await(scalar).until(Conditions.isNotNull);
        var selected = await(optional::call).until(present);
        var element = await(collection::call).until(singleElement);
        var entry = await(map::call).until(MapConditions.singleEntry);
        AwaitResult<Optional<String>, String> result = tryAwait(optional::call).until(present);

        assertEquals(5, actual.length());
        assertSame(text, actual);
        assertEquals(5, selected.length());
        assertEquals(5, element.length());
        assertEquals(5, entry.getKey().length());
        assertEquals(1, entry.getValue().intValue());
        assertEquals(selected, result.orThrow());
        assertSame(values, await(collection::call).until(CollectionConditions.nonEmpty));
        assertSame(entries, await(map::call).until(MapConditions.nonEmpty));
        assertEquals(selected, await(optional::call).until(present));
        var time = new FakeTime(0);
        List<Optional<String>> stages = await(optional::call).usingTime(time, time).every(ofNanos(1)).until(captured(present, present));
        assertEquals(List.of(Optional.of("ready"), Optional.of("ready")), stages);
    }

    @Test
    void constructorLambdasRetainTheirConcreteTypes() {
        StringBuilder text = Await.await(() -> new StringBuilder()).until(StringConditions.empty);
        ArrayList<String> values = Await.await(() -> new ArrayList<String>()).until(CollectionConditions.empty);
        LinkedHashMap<String, Integer> entries = Await.await(() -> new LinkedHashMap<String, Integer>()).until(MapConditions.empty);
        Optional<?> absent = Await.await(() -> Optional.empty()).until(OptionalConditions.absent);
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

        AwaitResult<Optional<String>, String> optionalResult = tryAwait(optional::call).until(present);
        AwaitResult<List<String>, String> collectionResult = tryAwait(collection::call).until(singleElement);
        AwaitResult<List<String>, String> firstResult = tryAwait(collection::call).until(firstElement);
        AwaitResult<List<String>, String> lastResult = tryAwait(collection::call).until(lastElement);
        AwaitResult<Map<String, Integer>, Map.Entry<String, Integer>> mapResult = tryAwait(map::call).until(MapConditions.singleEntry);

        for (var result : List.of(optionalResult, collectionResult, firstResult, lastResult)) {
            assertEquals("ready", result.orThrow());
        }
        assertEquals(Map.entry("ready", 1), mapResult.orThrow());
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
        String custom = await(() -> trim.apply(" ready ")).until(condition("ready", ignored -> ConditionResult.satisfied()));
        String asserted = await(() -> "ready").until(asserted(first.andThen(value -> calls.add("second " + value))::accept));
        Map<String, Integer> matchingEntry = await(() -> Map.of("ready", 1))
                .until(MapConditions.hasEntry(entry.and((key, value) -> !key.isBlank())::test));

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
        Callable<CheckedFunction<CharSequence, ConditionResult>> broadFactory = () -> ignored -> ConditionResult.satisfied();
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time);

        String direct = waiting.until(condition("direct", ignored -> ConditionResult.satisfied()));
        String created = waiting.until(conditionPerWait("created", PositiveFluentMatrixTest::evaluator));
        String concrete = waiting.until(conditionPerWait("broad", broadFactory));
        List<String> captured = waiting.until(captured(conditionPerWait("first", PositiveFluentMatrixTest::evaluator),
                conditionPerWait("second", PositiveFluentMatrixTest::evaluator)));

        assertEquals(List.of("ready", "ready", "ready"), List.of(direct, created, concrete));
        assertEquals(List.of("ready", "ready"), captured);
    }

    private record User(boolean active) {}

    @Test
    void inlinePredicatesInferTypesAcrossSourceCategoriesAndSequences() {
        var user = new User(true);
        var users = new ArrayList<>(List.of(user));
        var time = new FakeTime(0);

        User matched = await(() -> user).until(matches(value -> value.active()));
        ArrayList<User> allUsers = await(() -> users).until(all(value -> value.active()));
        User selected = await(() -> users).until(singleElement(value -> value.active()));
        User optional = await(() -> Optional.of(user)).until(hasValue(value -> value.active()));
        User mapped = await(() -> Map.of("id", user)).until(MapConditions.valueFor("id", value -> value.active()));
        ArrayList<User> nested = await(() -> users).until(all(matches(value -> value.active())));
        Map<String, User> entries = await(() -> Map.of("id", user))
                .until(MapConditions.allEntries((key, value) -> key.length() == 2 && value.active()));
        List<Integer> sortedValues = await(() -> List.of(1, 2)).until(sorted(java.util.Comparator.naturalOrder()));
        List<ArrayList<User>> stages = await(() -> users).usingTime(time, time).until(captured(
                singleElement(value -> value.active()), singleElement(value -> value.active())));

        for (User result : List.of(matched, selected, optional, mapped)) assertSame(user, result);
        assertSame(users, allUsers);
        assertSame(users, nested);
        assertSame(user, entries.get("id"));
        assertEquals(List.of(1, 2), sortedValues);
        assertEquals(List.of(users, users), stages);
    }

    @Test
    void typedSourceVariablesAndLambdasSelectTheSameValues() {
        var user = new User(true);
        var users = new ArrayList<>(List.of(user));
        var index = new LinkedHashMap<>(Map.of("id", user));
        Await.CollectionSource<ArrayList<User>> collection = () -> users;
        Await.OptionalSource<User> optional = () -> Optional.of(user);
        Await.MapSource<LinkedHashMap<String, User>> map = () -> index;

        User selected = await(collection).until(singleElement);
        User presentUser = await(optional).until(present);
        User mapped = await(map).until(MapConditions.valueFor("id"));
        ArrayList<User> originalUsers = await(collection).until(nonEmpty);
        LinkedHashMap<String, User> originalIndex = await(map).until(MapConditions.nonEmpty);

        assertSame(user, selected);
        assertSame(user, presentUser);
        assertSame(user, mapped);
        assertSame(users, originalUsers);
        assertSame(index, originalIndex);
    }

    private static CheckedFunction<String, ConditionResult> evaluator() {
        return ignored -> ConditionResult.satisfied();
    }

}
