package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.*;

class DirectBecauseTest {

    @Test
    void explainedComparisonsPreserveTheObservedTypeInBothTerminalsAndSequences() {
        Number actual = Integer.valueOf(1000);
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time);

        for (var condition : List.of(equalTo(1000), notEqualTo(0), sameAs(actual),
                notSameAs(0), in(1000, 2), notIn(0, -1))) {
            var check = condition.because("expected %s", "number");
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
    void explainedOptionalSelectionsReturnContainedNumbers() {
        Number actual = 1;
        var time = new FakeTime(0);
        var waiting = await(() -> Optional.of(actual)).usingTime(time, time);
        var check = hasValue(equalTo(1).because("inner")).because("outer");

        Number value = waiting.until(check);
        AwaitResult<Optional<Number>, Number> attempt = waiting.tryUntil(check);
        List<Number> sequence = waiting.until(present.because("first"), check, check);
        AwaitResult<Optional<Number>, List<Number>> attempts = waiting.tryUntil(check, check, check);

        assertSame(actual, value);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(List.of(actual, actual, actual), sequence);
        assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
    }

    @Test
    void explainedCollectionSelectionsReturnElementsInBothTerminalsAndSequences() {
        Number actual = 1;
        var time = new FakeTime(0);
        var waiting = await(() -> List.of(actual)).usingTime(time, time);

        Number nested = waiting.until(single(equalTo(1).because("inner")).because("outer"));
        Number indexed = waiting.until(element(0).because("index"));
        Number firstMatch = waiting.until(first((Number number) -> true).because("first"));
        Number lastMatch = waiting.until(last((Number number) -> true).because("last"));
        AwaitResult<List<Number>, Number> attempt = waiting.tryUntil(single(equalTo(1)).because("one"));
        List<Number> sequence = waiting.until(single.because("first"), single(equalTo(1)).because("second"), element(0));
        AwaitResult<List<Number>, List<Number>> attempts = waiting.tryUntil(
                first.because("first"), single(equalTo(1)).because("second"), last.because("last"));

        assertEquals(List.of(actual, actual, actual, actual), List.of(nested, indexed, firstMatch, lastMatch));
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(List.of(actual, actual, actual), sequence);
        assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
    }

    @Test
    void explainedMapSelectionsKeepValuesAndEntriesDistinct() {
        Number actual = 1;
        var time = new FakeTime(0);
        var waiting = await(() -> Map.of("key", actual)).usingTime(time, time);
        var check = valueFor("key", equalTo(1)).because("number");

        Number value = waiting.until(valueFor("key").because("value"));
        AwaitResult<Map<String, Number>, Number> attempt = waiting.tryUntil(check);
        List<Number> sequence = waiting.until(valueFor("key").because("first"), check, onlyValueFor("key").because("last"));
        AwaitResult<Map<String, Number>, List<Number>> attempts = waiting.tryUntil(check, check, check);
        Map.Entry<String, Number> entry = waiting.until(singleEntry.because("one entry"));
        Map.Entry<String, Number> matching = waiting.until(singleEntry(equalTo(Map.entry("key", 1))).because("entry"));
        Map.Entry<String, Number> byKey = waiting.until(entryFor("key").because("entry"));

        assertSame(actual, value);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        assertEquals(List.of(actual, actual, actual), sequence);
        assertEquals(sequence, assertInstanceOf(AwaitResult.Satisfied.class, attempts).result());
        assertEquals(Map.entry("key", actual), entry);
        assertEquals(entry, matching);
        assertEquals(entry, byKey);
    }

    @Test
    void explainedConditionsNarrowAndTransformNestedValues() {
        Number actual = 1;
        Integer direct = await(() -> actual).until(instanceOf(Integer.class).because("integer"));
        Integer optional = await(() -> Optional.of(actual)).until(hasValue(instanceOf(Integer.class)).because("integer"));
        Integer element = await(() -> List.of(actual)).until(single(instanceOf(Integer.class)).because("integer"));
        Integer mapped = await(() -> Map.of("key", actual)).until(valueFor("key", instanceOf(Integer.class)).because("integer"));
        String transformed = await(() -> List.of(actual)).until(single(yields(Number::toString)).because("text"));

        assertEquals(List.of(1, 1, 1, 1), List.of(direct, optional, element, mapped));
        assertEquals("1", transformed);
    }

    @Test
    void explainedMapQuantifiersPreserveBothTypeArgumentsAndTheConcreteMap() {
        var actual = new LinkedHashMap<>(Map.of(" ", "\t"));
        var waiting = await(() -> actual);

        List<LinkedHashMap<String, String>> results = List.of(
                waiting.until(allKeys(String::isBlank).because("blank keys")),
                waiting.until(anyKey(String::isBlank).because("a blank key")),
                waiting.until(noKey(String::isEmpty).because("nonempty keys")),
                waiting.until(allValues(String::isBlank).because("blank values")),
                waiting.until(anyValue(String::isBlank).because("a blank value")),
                waiting.until(noValue(String::isEmpty).because("nonempty values")));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void explainedEntryQuantifiersAcceptPredicatesForSupertypes() {
        var actual = new LinkedHashMap<>(Map.of("key", 1));
        var waiting = await(() -> actual);
        BiPredicate<CharSequence, Number> valid = (key, value) -> !key.isEmpty() && value.intValue() > 0;

        List<LinkedHashMap<String, Integer>> results = List.of(
                waiting.until(allEntries(valid).because("valid entries")),
                waiting.until(anyEntry(valid).because("a valid entry")),
                waiting.until(noEntry(valid.negate()).because("no invalid entries")));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void explainedCollectionQuantifiersAcceptPredicatesForSupertypes() {
        var actual = new ArrayList<>(List.of(""));
        var waiting = await(() -> actual);

        List<ArrayList<String>> results = List.of(
                waiting.until(all(CharSequence::isEmpty).because("empty elements")),
                waiting.until(any(CharSequence::isEmpty).because("an empty element")),
                waiting.until(none(not(CharSequence::isEmpty)).because("no nonempty elements")));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void standaloneConditionsKeepTheirTypesWhenExplainedAndNested() {
        record Payment(String id, boolean paid) {}
        var actual = new Payment("receipt", true);
        var paid = matches(Payment::paid);
        var id = yields(Payment::id);
        var onePaid = single(paid.because("paid payment"));
        var oneId = single(id.because("payment id"));
        var optionalId = hasValue(id.because("optional payment id"));
        var mappedId = valueFor("key", id.because("mapped payment id"));

        Payment payment = await(() -> actual).until(paid.because("required payment"));
        Payment selected = await(() -> List.of(actual)).until(onePaid.because("one payment"));
        String value = await(() -> actual).until(id.because("required id"));
        String nested = await(() -> List.of(actual)).until(oneId.because("one id"));
        String optional = await(() -> Optional.of(actual)).until(optionalId.because("optional id"));
        String mapped = await(() -> Map.of("key", actual)).until(mappedId.because("mapped id"));

        assertSame(actual, payment);
        assertSame(actual, selected);
        assertEquals(List.of("receipt", "receipt", "receipt", "receipt"), List.of(value, nested, optional, mapped));
        assertNull(ConditionTestRuntime.explanation(paid));
        assertNull(ConditionTestRuntime.explanation(id));
    }

    @Test
    void standaloneSortingAndEntryConditionsDoNotNeedSourceTypeHints() {
        record Receipt(String key, Integer value) implements Map.Entry<String, Integer> {
            @Override public String getKey() { return key; }
            @Override public Integer getValue() { return value; }
            @Override public Integer setValue(Integer value) { throw new UnsupportedOperationException(); }
        }
        var entry = new Receipt("key", 1);
        var actual = new ProbeContainers.EntryMap<String, Integer>(List.of(entry));
        var receipt = singleEntry(instanceOf(Receipt.class).because("receipt entry"));
        var key = singleEntry(yields(DirectBecauseTest::entryKey).because("entry key"));
        var ordered = sorted(Comparator.comparingInt(Number::intValue));
        var numbers = new ArrayList<>(List.of(1, 2));

        Receipt result = await(() -> actual).until(receipt.because("required receipt"));
        String resultKey = await(() -> actual).until(key.because("required key"));
        ArrayList<Integer> sortedNumbers = await(() -> numbers).until(ordered.because("ordered numbers"));

        assertSame(entry, result);
        assertEquals("key", resultKey);
        assertSame(numbers, sortedNumbers);
    }

    private static String entryKey(Map.Entry<? extends String, ?> entry) {
        return entry.getKey();
    }

    @Test
    void explainedSelectionsAcceptCovariantViewsAndTypedEntryCallbacks() {
        Source<List<? extends Number>> values = () -> List.of(1);
        Source<Map<? extends CharSequence, ? extends Number>> entries = () -> Map.of("key", 1);
        var collection = new CollectionViewSource<Number, List<? extends Number>>(values);
        var map = new MapViewSource<CharSequence, Number, Map<? extends CharSequence, ? extends Number>>(entries);

        Number value = await(collection).until(single(equalTo(1)).because("one"));
        Number mapped = await(map).until(valueFor("key", equalTo(1)).because("one"));
        Map.Entry<? extends CharSequence, ? extends Number> entry = await(map).until(entryFor("key").because("entry"));
        Map.Entry<String, Number> checked = await(() -> Map.<String, Number>of("key", 1)).until(singleEntry(matches(
                (Map.Entry<? extends String, ? extends Number> candidate) -> candidate.getValue() != null)).because("checked"));
        Number transformed = await(() -> Map.<String, Number>of("key", 1)).until(singleEntry(yields(
                (Map.Entry<? extends String, ? extends Number> candidate) -> candidate.getValue())).because("transformed"));
        Integer subtype = await(() -> List.of(1)).until(first((Number number) -> true).because("first"));

        assertEquals(List.of(1, 1, 1, 1), List.of(value, mapped, transformed, subtype));
        assertEquals(Map.entry("key", 1), entry);
        assertEquals(entry, checked);
    }
}
