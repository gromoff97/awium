package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.CollectionViewSource;
import static io.github.gromoff97.awium.Await.MapViewSource;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.MapConditions.*;
import static io.github.gromoff97.awium.OptionalConditions.*;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.util.AbstractMap.SimpleImmutableEntry;
import static java.util.Comparator.comparingInt;
import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.*;

class FluentConditionTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void typedComparisonsPreserveTheObservedTypeInBothTerminalsAndSequences() {
        Number actual = Integer.valueOf(1000);
        var waiting = await(() -> actual).usingTime(time, time);

        for (var condition : List.of(equalTo(1000), notEqualTo(0), sameAs(actual),
                notSameAs(0), in(List.of(1000, 2)), notIn(List.of(0, -1)))) {
            var check = condition.because("numeric identity must be preserved");
            Number value = waiting.until(check);
            AwaitResult<Number, Number> attempt = diagnostic(waiting).until(check);
            List<Number> sequence = waiting.until(captured(check, check, check));
            AwaitResult<Number, List<Number>> attempts = diagnostic(waiting).until(captured(check, check, check));

            assertSame(actual, value);
            assertSame(actual, attempt.orThrow());
            assertEquals(List.of(actual, actual, actual), sequence);
            assertEquals(sequence, attempts.orThrow());
            assertEquals(3, attempts(attempts).size());
        }
    }

    @Test
    void inferredResultsStayTypedAcrossSeparateSequenceFamilies() {
        var value = await(() -> "ready").until(equalTo("ready"));
        var comparisons = await(() -> "ready").usingTime(time, time)
                .until(captured(equalTo("ready"), notIn(List.of("failed"))));
        var checks = await(() -> "ready").usingTime(time, time)
                .until(captured(isNotNull, matches(valueToCheck -> true)));
        assertEquals(5, value.length());
        assertEquals(5, comparisons.getFirst().length());
        assertEquals(5, checks.getLast().length());
    }

    @Test
    void typedNullableOperandsKeepTheirTypes() {
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
        var waiting = await(() -> Optional.of(actual)).usingTime(time, time);
        var check = hasValue(equalTo(1)).because("optional contains the expected number");

        Number value = waiting.until(check);
        AwaitResult<Optional<Number>, Number> attempt = diagnostic(waiting).until(check);
        List<Optional<Number>> sequence = waiting.until(captured(present, check, check));
        AwaitResult<Optional<Number>, List<Optional<Number>>> attempts = diagnostic(waiting).until(captured(check, check, check));

        assertSame(actual, value);
        assertSame(actual, attempt.orThrow());
        assertEquals(List.of(Optional.of(actual), Optional.of(actual), Optional.of(actual)), sequence);
        assertEquals(sequence, attempts.orThrow());
    }

    @Test
    void typedCollectionSelectionsReturnElementsAndCapturesKeepTheirContainers() {
        Number actual = 1;
        var waiting = await(() -> List.of(actual)).usingTime(time, time);

        Number nested = waiting.until(singleElement(equalTo(1)));
        Number indexed = waiting.until(elementAt(0));
        Number firstMatch = waiting.until(firstElement((Number number) -> true));
        Number lastMatch = waiting.until(lastElement((Number number) -> true));
        AwaitResult<List<Number>, Number> attempt = diagnostic(waiting).until(singleElement(equalTo(1)));
        List<List<Number>> sequence = waiting.until(captured(singleElement, singleElement(equalTo(1)), elementAt(0)));
        AwaitResult<List<Number>, List<List<Number>>> attempts = diagnostic(waiting).until(captured(
                firstElement, singleElement(equalTo(1)), lastElement));

        assertEquals(List.of(actual, actual, actual, actual), List.of(nested, indexed, firstMatch, lastMatch));
        assertSame(actual, attempt.orThrow());
        assertEquals(List.of(List.of(actual), List.of(actual), List.of(actual)), sequence);
        assertEquals(sequence, attempts.orThrow());
    }

    @Test
    void typedMapSelectionsKeepValuesAndEntriesDistinct() {
        Number actual = 1;
        var waiting = await(() -> Map.of("key", actual)).usingTime(time, time);
        var check = valueFor("key", equalTo(1)).because("key selects the expected number");

        Number value = waiting.until(valueFor("key"));
        AwaitResult<Map<String, Number>, Number> attempt = diagnostic(waiting).until(check);
        List<Map<String, Number>> sequence = waiting.until(captured(valueFor("key"), check));
        AwaitResult<Map<String, Number>, List<Map<String, Number>>> attempts = diagnostic(waiting).until(captured(check, check));
        Map.Entry<String, Number> entry = waiting.until(singleEntry);
        Map.Entry<String, Number> matching = waiting.until(singleEntry(equalTo(Map.entry("key", 1))));
        Map.Entry<String, Number> byKey = waiting.until(entryFor("key"));

        assertSame(actual, value);
        assertSame(actual, attempt.orThrow());
        assertEquals(List.of(Map.of("key", actual), Map.of("key", actual)), sequence);
        assertEquals(sequence, attempts.orThrow());
        assertEquals(Map.entry("key", actual), entry);
        assertEquals(entry, matching);
        assertEquals(entry, byKey);
    }

    @Test
    void typedConditionsNarrowNestedValues() {
        Number actual = 1;
        Integer direct = await(() -> actual).usingTime(time, time).until(instanceOf(Integer.class));
        Integer optional = await(() -> Optional.of(actual)).usingTime(time, time).until(hasValue(instanceOf(Integer.class)));
        Integer element = await(() -> List.of(actual)).usingTime(time, time).until(singleElement(instanceOf(Integer.class)));
        Integer mapped = await(() -> Map.of("key", actual)).usingTime(time, time).until(valueFor("key", instanceOf(Integer.class)));

        assertEquals(List.of(1, 1, 1, 1), List.of(direct, optional, element, mapped));
    }

    @Test
    void typedMapQuantifiersPreserveBothTypeArgumentsAndTheConcreteMap() {
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
        var actual = new LinkedHashMap<>(Map.of("key", 1));
        var waiting = await(() -> actual).usingTime(time, time);
        BiPredicate<CharSequence, Number> valid = (key, value) -> !key.isEmpty() && value.intValue() > 0;

        List<LinkedHashMap<String, Integer>> results = List.of(
                waiting.until(allEntries(valid::test)),
                waiting.until(hasEntry(valid::test)),
                waiting.until(hasNoEntry(valid.negate()::test)));

        results.forEach(result -> assertSame(actual, result));
    }

    @Test
    void typedCollectionQuantifiersAcceptPredicatesForSupertypes() {
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
        var check = condition("identity", (Payment value) -> satisfied(value));

        var payment = await(() -> actual).usingTime(time, time).until(condition("inline", value -> satisfied(value)));
        var created = await(() -> actual).usingTime(time, time).until(conditionPerWait("factory", () -> ignored -> satisfied(ignored)));
        var selected = await(() -> List.of(actual)).usingTime(time, time).until(singleElement(check));
        var optional = await(() -> Optional.of(actual)).usingTime(time, time).until(hasValue(check));
        var mapped = await(() -> Map.of("key", actual)).usingTime(time, time).until(valueFor("key", check));
        var sequence = await(() -> actual).usingTime(time, time)
                .until(captured(check, condition("second", ignored -> satisfied(ignored))));
        var diagnostic = tryAwait(() -> actual).usingTime(time, time).until(check);

        assertEquals(List.of("receipt", "receipt", "receipt", "receipt", "receipt", "receipt"),
                List.of(payment.id(), created.id(), selected.id(), optional.id(), mapped.id(), sequence.getLast().id()));
        var value = switch (diagnostic) {
            case Succeeded(var history, var observed) -> observed;
            case Failed(var history, var failure) -> throw new AssertionError(failure.message());
        };
        assertEquals("receipt", value.id());
        assertSame(actual, value);
        assertSame(actual, payment);
        assertSame(actual, selected);
    }

    @Test
    void sourceProjectionsKeepTheirTypesWithTypedChecks() {
        record User(String name) {}
        var user = new User("Alice");
        var entries = Map.of("key", 1);

        var name = await(user::name).until(isNotNull);
        var key = await(entries::keySet).until(singleElement);

        assertEquals(5, name.length());
        assertEquals(3, key.length());
    }

    @Test
    void standaloneSortingAndEntryConditionsDoNotNeedSourceTypeHints() {
        final class Receipt extends SimpleImmutableEntry<String, Integer> {
            Receipt(String key, Integer value) { super(key, value); }
        }
        var entry = new Receipt("key", 1);
        var actual = new ProbeContainers.EntryMap<String, Integer>(List.of(entry));
        var receipt = singleEntry(instanceOf(Receipt.class));
        var ordered = sorted(comparingInt(Number::intValue));
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
        Callable<List<? extends Number>> values = () -> List.of(1);
        Callable<Map<CharSequence, ? extends Number>> entries = () -> Map.of("key", 1);
        var collection = new CollectionViewSource<Number, List<? extends Number>>(values);
        var map = new MapViewSource<CharSequence, Number, Map<CharSequence, ? extends Number>>(entries);

        Number value = await(collection).usingTime(time, time).until(singleElement(isNotNull));
        Number mapped = await(map).usingTime(time, time).until(valueFor("key", isNotNull));
        Map.Entry<? extends CharSequence, ? extends Number> entry = await(map).usingTime(time, time).until(entryFor("key"));
        Map.Entry<String, Number> checked = await(() -> Map.<String, Number>of("key", 1)).usingTime(time, time).until(singleEntry(matches(
                (Map.Entry<? extends String, ? extends Number> candidate) -> candidate.getValue() != null)));
        Integer subtype = await(() -> List.of(1)).usingTime(time, time).until(firstElement((Number number) -> true));

        assertEquals(List.of(1, 1, 1), List.of(value, mapped, subtype));
        assertEquals(Map.entry("key", 1), entry);
        assertEquals(entry, checked);
    }
}
