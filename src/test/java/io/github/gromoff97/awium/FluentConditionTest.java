package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Await.CollectionViewSource;
import io.github.gromoff97.awium.Await.MapViewSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.*;

class FluentConditionTest {

    @Test
    void typedComparisonsPreserveTheObservedTypeInBothTerminalsAndSequences() {
        Number actual = Integer.valueOf(1000);
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time);

        for (var condition : List.of(equalTo(1000), notEqualTo(0), sameAs(actual),
                notSameAs(0), in(List.of(1000, 2)), notIn(List.of(0, -1)))) {
            var check = condition;
            Number value = waiting.until(check);
            AwaitResult<Number, Number> attempt = waiting.tryUntil(check);
            List<Number> sequence = waiting.until(check, check, check);
            AwaitResult<Number, List<Number>> attempts = waiting.tryUntil(check, check, check);

            assertSame(actual, value);
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
            assertEquals(List.of(actual, actual, actual), sequence);
            assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
            assertEquals(3, attempts.totalAttempts());
        }
    }

    @Test
    void inferredResultsStayTypedAcrossSeparateSequenceFamilies() {
        var time = new FakeTime(0);
        var value = await(() -> "ready").until(equalTo("ready"));
        var comparisons = await(() -> "ready").usingTime(time, time)
                .until(equalTo("ready"), notIn(List.of("failed")));
        var checks = await(() -> "ready").usingTime(time, time)
                .until(isNotNull, matches(valueToCheck -> true));
        assertEquals(5, value.length());
        assertEquals(5, comparisons.getFirst().length());
        assertEquals(5, checks.getLast().length());
    }

    @Test
    void typedNullableOperandsKeepTheirTypes() {
        var time = new FakeTime(0);
        String missing = null;
        var value = await(() -> missing).usingTime(time, time).until(equalTo(missing));
        var actual = java.util.Collections.singletonMap(missing, "ready");
        var selected = await(() -> actual).usingTime(time, time).until(valueFor(missing));

        String nullableResult = value;
        assertNull(nullableResult);
        assertEquals(5, selected.length());
    }

    @Test
    void typedOptionalSelectionsReturnContainedNumbers() {
        Number actual = 1;
        var time = new FakeTime(0);
        var waiting = await(() -> Optional.of(actual)).usingTime(time, time);
        var check = hasValue(equalTo(1));

        Number value = waiting.until(check);
        AwaitResult<Optional<Number>, Number> attempt = waiting.tryUntil(check);
        List<Number> sequence = waiting.until(present, check, check);
        AwaitResult<Optional<Number>, List<Number>> attempts = waiting.tryUntil(check, check, check);

        assertSame(actual, value);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(List.of(actual, actual, actual), sequence);
        assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
    }

    @Test
    void typedCollectionSelectionsReturnElementsInBothTerminalsAndSequences() {
        Number actual = 1;
        var time = new FakeTime(0);
        var waiting = await(() -> List.of(actual)).usingTime(time, time);

        Number nested = waiting.until(single(equalTo(1)));
        Number indexed = waiting.until(element(0));
        Number firstMatch = waiting.until(first((Number number) -> true));
        Number lastMatch = waiting.until(last((Number number) -> true));
        AwaitResult<List<Number>, Number> attempt = waiting.tryUntil(single(equalTo(1)));
        List<Number> sequence = waiting.until(single, single(equalTo(1)), element(0));
        AwaitResult<List<Number>, List<Number>> attempts = waiting.tryUntil(
                first, single(equalTo(1)), last);

        assertEquals(List.of(actual, actual, actual, actual), List.of(nested, indexed, firstMatch, lastMatch));
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(List.of(actual, actual, actual), sequence);
        assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
    }

    @Test
    void typedMapSelectionsKeepValuesAndEntriesDistinct() {
        Number actual = 1;
        var time = new FakeTime(0);
        var waiting = await(() -> Map.of("key", actual)).usingTime(time, time);
        var check = valueFor("key", equalTo(1));

        Number value = waiting.until(valueFor("key"));
        AwaitResult<Map<String, Number>, Number> attempt = waiting.tryUntil(check);
        List<Number> sequence = waiting.until(valueFor("key"), check, onlyValueFor("key"));
        AwaitResult<Map<String, Number>, List<Number>> attempts = waiting.tryUntil(check, check, check);
        Map.Entry<String, Number> entry = waiting.until(singleEntry);
        Map.Entry<String, Number> matching = waiting.until(singleEntry(equalTo(Map.entry("key", 1))));
        Map.Entry<String, Number> byKey = waiting.until(entryFor("key"));

        assertSame(actual, value);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(List.of(actual, actual, actual), sequence);
        assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
        assertEquals(Map.entry("key", actual), entry);
        assertEquals(entry, matching);
        assertEquals(entry, byKey);
    }

    @Test
    void typedConditionsNarrowNestedValues() {
        var time = new FakeTime(0);
        Number actual = 1;
        Integer direct = await(() -> actual).usingTime(time, time).until(instanceOf(Integer.class));
        Integer optional = await(() -> Optional.of(actual)).usingTime(time, time).until(hasValue(instanceOf(Integer.class)));
        Integer element = await(() -> List.of(actual)).usingTime(time, time).until(single(instanceOf(Integer.class)));
        Integer mapped = await(() -> Map.of("key", actual)).usingTime(time, time).until(valueFor("key", instanceOf(Integer.class)));

        assertEquals(List.of(1, 1, 1, 1), List.of(direct, optional, element, mapped));
    }

    @Test
    void typedMapQuantifiersPreserveBothTypeArgumentsAndTheConcreteMap() {
        var time = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of(" ", "\t"));
        var waiting = await(() -> actual).usingTime(time, time);

        List<LinkedHashMap<String, String>> results = List.of(
                waiting.until(allKeys(String::isBlank)),
                waiting.until(anyKey(String::isBlank)),
                waiting.until(noKey(String::isEmpty)),
                waiting.until(allValues(String::isBlank)),
                waiting.until(anyValue(String::isBlank)),
                waiting.until(noValue(String::isEmpty)));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void typedEntryQuantifiersAcceptPredicatesForSupertypes() {
        var time = new FakeTime(0);
        var actual = new LinkedHashMap<>(Map.of("key", 1));
        var waiting = await(() -> actual).usingTime(time, time);
        BiPredicate<CharSequence, Number> valid = (key, value) -> !key.isEmpty() && value.intValue() > 0;

        List<LinkedHashMap<String, Integer>> results = List.of(
                waiting.until(allEntries(valid::test)),
                waiting.until(anyEntry(valid::test)),
                waiting.until(noEntry(valid.negate()::test)));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void typedCollectionQuantifiersAcceptPredicatesForSupertypes() {
        var time = new FakeTime(0);
        var actual = new ArrayList<>(List.of(""));
        var waiting = await(() -> actual).usingTime(time, time);

        List<ArrayList<String>> results = List.of(
                waiting.until(all(CharSequence::isEmpty)),
                waiting.until(any(CharSequence::isEmpty)),
                waiting.until(none(not(CharSequence::isEmpty)::test)));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void customChecksKeepInferredTypesThroughComposition() {
        record Payment(String id) {}
        var actual = new Payment("receipt");
        var check = condition("identity", ConditionResult::satisfied);
        var time = new FakeTime(0);

        var payment = await(() -> actual).usingTime(time, time).until(condition("inline", value -> ConditionResult.satisfied(value)));
        var created = await(() -> actual).usingTime(time, time).until(conditionPerWait("factory", () -> ConditionResult::satisfied));
        var selected = await(() -> List.of(actual)).usingTime(time, time).until(single(check));
        var optional = await(() -> Optional.of(actual)).usingTime(time, time).until(hasValue(check));
        var mapped = await(() -> Map.of("key", actual)).usingTime(time, time).until(valueFor("key", check));
        var sequence = await(() -> actual).usingTime(time, time)
                .until(check, condition("second", ConditionResult::satisfied));
        var diagnostic = await(() -> actual).usingTime(time, time).tryUntil(check);

        assertEquals(List.of("receipt", "receipt", "receipt", "receipt", "receipt", "receipt"),
                List.of(payment.id(), created.id(), selected.id(), optional.id(), mapped.id(), sequence.getLast().id()));
        if (diagnostic instanceof AwaitResult.Satisfied(var attempts, var value)) {
            assertEquals("receipt", value.id());
            assertSame(actual, value);
        } else {
            fail("custom check must succeed");
        }
        assertSame(actual, payment);
        assertSame(actual, selected);
    }

    @Test
    void sourceProjectionsKeepTheirTypesWithTypedChecks() {
        record User(String name) {}
        var user = new User("Alice");
        var entries = Map.of("key", 1);

        var name = await(user::name).until(isNotNull);
        var key = await(entries::keySet).until(single);

        assertEquals(5, name.length());
        assertEquals(3, key.length());
    }

    @Test
    void standaloneSortingAndEntryConditionsDoNotNeedSourceTypeHints() {
        var time = new FakeTime(0);
        record Receipt(String key, Integer value) implements Map.Entry<String, Integer> {
            @Override public String getKey() { return key; }
            @Override public Integer getValue() { return value; }
            @Override public Integer setValue(Integer value) { throw new UnsupportedOperationException(); }
        }
        var entry = new Receipt("key", 1);
        var actual = new ProbeContainers.EntryMap<String, Integer>(List.of(entry));
        var receipt = singleEntry(instanceOf(Receipt.class));
        var ordered = sorted(Comparator.comparingInt(Number::intValue));
        var numbers = new ArrayList<>(List.of(1, 2));
        var words = new ArrayList<>(List.of("a", "b"));

        Receipt result = await(() -> actual).usingTime(time, time).until(receipt);
        ArrayList<Integer> sortedNumbers = await(() -> numbers).usingTime(time, time).until(ordered);
        ArrayList<String> sortedWords = await(() -> words).usingTime(time, time).until(sorted(String::compareTo));

        assertSame(entry, result);
        assertSame(numbers, sortedNumbers);
        assertSame(words, sortedWords);
    }

    @Test
    void typedSelectionsAcceptCovariantViewsAndTypedEntryCallbacks() {
        var time = new FakeTime(0);
        Callable<List<? extends Number>> values = () -> List.of(1);
        Callable<Map<CharSequence, ? extends Number>> entries = () -> Map.of("key", 1);
        var collection = new CollectionViewSource<Number, List<? extends Number>>(values);
        var map = new MapViewSource<CharSequence, Number, Map<CharSequence, ? extends Number>>(entries);

        Number value = await(collection).usingTime(time, time).until(single(isNotNull));
        Number mapped = await(map).usingTime(time, time).until(valueFor("key", isNotNull));
        Map.Entry<? extends CharSequence, ? extends Number> entry = await(map).usingTime(time, time).until(entryFor("key"));
        Map.Entry<String, Number> checked = await(() -> Map.<String, Number>of("key", 1)).usingTime(time, time).until(singleEntry(matches(
                (Map.Entry<? extends String, ? extends Number> candidate) -> candidate.getValue() != null)));
        Integer subtype = await(() -> List.of(1)).usingTime(time, time).until(first((Number number) -> true));

        assertEquals(List.of(1, 1, 1), List.of(value, mapped, subtype));
        assertEquals(Map.entry("key", 1), entry);
        assertEquals(entry, checked);
    }
}
