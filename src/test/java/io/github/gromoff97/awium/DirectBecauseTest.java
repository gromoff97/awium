package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Source.CollectionViewSource;
import io.github.gromoff97.awium.Source.MapViewSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
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
    void explainedSelectionsAcceptCovariantViewsAndTypedEntryCallbacks() {
        Source<List<? extends Number>> values = () -> List.of(1);
        Source<Map<? extends CharSequence, ? extends Number>> entries = () -> Map.of("key", 1);
        var collection = new CollectionViewSource<Number, List<? extends Number>>(values);
        var map = new MapViewSource<CharSequence, Number, Map<? extends CharSequence, ? extends Number>>(entries);

        Number value = await(collection).until(single(equalTo(1)).because("one"));
        Number mapped = await(map).until(valueFor("key", equalTo(1)).because("one"));
        Map.Entry<? extends CharSequence, ? extends Number> entry = await(map).until(entryFor("key").because("entry"));
        Map.Entry<String, Number> checked = await(() -> Map.<String, Number>of("key", 1)).until(singleEntry(matches(
                (Map.Entry<String, Number> candidate) -> candidate.getValue() != null)).because("checked"));
        Number transformed = await(() -> Map.<String, Number>of("key", 1)).until(singleEntry(yields(
                (Map.Entry<String, Number> candidate) -> candidate.getValue())).because("transformed"));
        Integer subtype = await(() -> List.of(1)).until(first((Number number) -> true).because("first"));

        assertEquals(List.of(1, 1, 1, 1), List.of(value, mapped, transformed, subtype));
        assertEquals(Map.entry("key", 1), entry);
        assertEquals(entry, checked);
    }
}
