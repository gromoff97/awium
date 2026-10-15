package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Source.CollectionSource;
import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import io.github.gromoff97.awium.Source.OptionalSource;
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

import static io.github.gromoff97.awium.Await.await;
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
    void objectFacadeExecutesEveryValidConfigurationPath() {
        var pollingTime = new FakeTime(0);
        var actual = new Object();
        Source<Object> source = () -> actual;

        assertAllSame(actual,
                await(source).usingTime(pollingTime, pollingTime).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).until(isNotNull.because("object every")),
                await(source).usingTime(pollingTime, pollingTime).upTo(UP_TO).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).persisting(ZERO).until(isNotNull.because("object must remain available")),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).persisting(ZERO).until(isNotNull.because("object must remain available")),
                await(source).usingTime(pollingTime, pollingTime).upTo(UP_TO).persisting(ZERO).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull.because("object all")),
                await(source).usingTime(pollingTime, pollingTime).persisting(ZERO).upTo(UP_TO).every(EVERY).until(isNotNull),
                await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).every(EVERY).upTo(UP_TO).persisting(ZERO).until(isNotNull));

        Condition<Object, Object> selecting = condition(
                "select actual", ConditionResult::satisfied);
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(selecting.because("selected object")));
        Void nil = await((Source<Object>) () -> null).usingTime(pollingTime, pollingTime).until(isNull);
        assertSame(null, nil);
    }

    @Test
    void optionalFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var value = new String("value");
        OptionalSource<String> source = () -> Optional.of(value);

        String selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(present.because("optional full chain"));
        Void absentValue = await((OptionalSource<String>) Optional::empty).usingTime(pollingTime, pollingTime).until(absent);

        assertSame(value, selected);
        assertSame(null, absentValue);
    }

    @Test
    void collectionFacadeExecutesCanonicalFullChain() {
        var pollingTime = new FakeTime(0);
        var actual = new ArrayList<>(List.of("value"));
        CollectionSource<ArrayList<String>> source = () -> actual;

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
        MapSource<LinkedHashMap<String, String>> source = () -> actual;

        LinkedHashMap<String, String> raw = await(source).usingTime(pollingTime, pollingTime).until(MapConditions.nonEmpty);
        LinkedHashMap<String, String> selected = await(source).usingTime(pollingTime, pollingTime).every(EVERY).upTo(UP_TO).persisting(ZERO).until(MapConditions.nonEmpty.because("map full chain"));

        assertSame(actual, raw);
        assertSame(actual, selected);
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
        assertSame(element, await(collectionSource).usingTime(time, time).until(single));
        assertSame(element, assertInstanceOf(AwaitResult.Satisfied.class,
                await(collectionSource).usingTime(time, time).tryUntil(single)).result());
        assertSame(map, await(mapSource).usingTime(time, time).until(MapConditions.nonEmpty));
        assertSame(entry, await(mapSource).usingTime(time, time).until(MapConditions.singleEntry));
        assertSame(entry, assertInstanceOf(AwaitResult.Satisfied.class,
                await(mapSource).usingTime(time, time).tryUntil(MapConditions.singleEntry)).result());
        assertEquals(3, collectionCalls[0]);
        assertEquals(3, mapCalls[0]);
    }

    @Test
    void methodReferencesRecoverSelectionTypesFromPlainSources() {
        Source<Optional<String>> optional = () -> Optional.of("ready");
        Source<ArrayList<String>> collection = () -> new ArrayList<>(List.of("ready"));
        Source<LinkedHashMap<String, Integer>> map = () -> new LinkedHashMap<>(Map.of("ready", 1));

        Object untypedOptional = await(optional).until(present);
        Object untypedElement = await(collection).until(single);
        Object untypedEntry = await(map).until(MapConditions.singleEntry);
        String optionalValue = await(optional::get).until(present.because("required value"));
        String element = await(collection::get).until(single.because("unique value"));
        Map.Entry<String, Integer> entry = await(map::get).until(MapConditions.singleEntry.because("unique entry"));
        AwaitResult<Optional<String>, String> attempt = await(optional::get).tryUntil(present);

        assertEquals("ready", optionalValue);
        assertEquals(optionalValue, untypedOptional);
        assertEquals("ready", element);
        assertEquals(element, untypedElement);
        assertEquals(Map.entry("ready", 1), entry);
        assertEquals(entry, untypedEntry);
        assertEquals(optionalValue, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
    }

    @Test
    void diagnosticTerminalsRetainSpecializedSourceAndResultTypes() {
        OptionalSource<String> optional = () -> Optional.of("ready");
        CollectionSource<List<String>> collection = () -> List.of("ready");
        MapSource<Map<String, Integer>> map = () -> Map.of("ready", 1);

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

        String matched = await(() -> "ready").until(matches(Predicate.not(text).negate()));
        String transformed = await(() -> " ready ").until(yields(Function.<String>identity().andThen(trim)::apply));
        String custom = await(() -> " ready ").until(condition("trimmed", trim.andThen(ConditionResult::satisfied)::apply));
        String asserted = await(() -> "ready").until(asserted(first.andThen(value -> calls.add("second " + value))::accept));
        Map<String, Integer> matchingEntry = await(() -> Map.of("ready", 1))
                .until(MapConditions.anyEntry(entry.and((key, value) -> !key.isBlank())));

        assertEquals(List.of("ready", "ready", "ready", "ready"), List.of(matched, transformed, custom, asserted));
        assertEquals(List.of("first ready", "second ready"), calls);
        assertEquals(Map.of("ready", 1), matchingEntry);
    }

    @Test
    void optionalValuesCanThemselvesBeClassesConditionsOrPredicates() {
        Class<?> expectedClass = String.class;
        Condition<String, String> expectedCondition = yields(String::trim);
        Predicate<String> expectedPredicate = String::isBlank;

        Class<?> type = await(() -> Optional.of(expectedClass)).until(hasValue(equalTo(expectedClass)));
        Condition<String, String> condition = await(() -> Optional.of(expectedCondition)).until(hasValue(equalTo(expectedCondition)));
        Predicate<String> predicate = await(() -> Optional.of(expectedPredicate)).until(hasValue(equalTo(expectedPredicate)));

        assertSame(expectedClass, type);
        assertSame(expectedCondition, condition);
        assertSame(expectedPredicate, predicate);
    }

    @Test
    void evaluatorFactoriesPreserveOrTransformResultsAccordingToTheirContract() {
        Callable<CheckedFunction<String, ConditionResult<Integer>>> factory = () -> value -> ConditionResult.satisfied(value.length());
        Callable<CheckedFunction<CharSequence, ConditionResult<CharSequence>>> broadFactory = () -> ignored -> ConditionResult.satisfied("replacement");
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time);

        String direct = waiting.until(condition("direct", ConditionResult::satisfied));
        String created = waiting.until(condition("created", PositiveFluentMatrixTest::evaluator).because("ready"));
        Integer transformed = waiting.until(condition("transformed", factory));
        String preserved = waiting.until(preserving("preserved", PositiveFluentMatrixTest::evaluator));
        String concrete = waiting.until(preserving("broad", broadFactory));
        List<String> captured = waiting.until(condition("first", PositiveFluentMatrixTest::evaluator),
                condition("second", PositiveFluentMatrixTest::evaluator));

        assertEquals(List.of("ready", "ready", "ready", "ready"), List.of(direct, created, preserved, concrete));
        assertEquals(5, transformed);
        assertEquals(List.of("ready", "ready"), captured);
    }

    private static CheckedFunction<String, ConditionResult<String>> evaluator() {
        return ConditionResult::satisfied;
    }

    private static void assertAllSame(Object expected, Object... actuals) {
        for (Object actual : actuals) {
            assertSame(expected, actual);
        }
    }
}
