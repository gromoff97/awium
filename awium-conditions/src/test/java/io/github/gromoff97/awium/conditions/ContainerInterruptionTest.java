package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.conditions.CollectionConditions;
import io.github.gromoff97.awium.conditions.MapConditions;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.conditions.CollectionConditions.*;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.failure;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.thrown;
import static io.github.gromoff97.awium.conditions.Conditions.captured;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.MapConditions.allEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.allKeys;
import static io.github.gromoff97.awium.conditions.MapConditions.allValues;
import static io.github.gromoff97.awium.conditions.MapConditions.containsAllEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.containsExactlyEntries;
import static io.github.gromoff97.awium.conditions.MapConditions.containsKey;
import static io.github.gromoff97.awium.conditions.MapConditions.containsOnlyKeys;
import static io.github.gromoff97.awium.conditions.MapConditions.containsValue;
import static io.github.gromoff97.awium.conditions.MapConditions.entryFor;
import static io.github.gromoff97.awium.conditions.MapConditions.hasEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.singleEntry;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.util.Collections.singletonList;
import static java.util.Map.Entry;
import static java.util.Objects.checkIndex;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class ContainerInterruptionTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @ParameterizedTest
    @MethodSource("quantifiers")
    void interruptionBeforeTheFirstNestedCheckSkipsItsFactory(
            Function<AwaitCondition.InputCondition<String>, PreservingCondition<Collection<? extends String>>> quantify) {
        var actual = new AbstractList<String>() {
            @Override public int size() { return 1; }
            @Override public String get(int index) {
                checkIndex(index, size());
                currentThread().interrupt();
                return "ready";
            }
            @Override public String toString() { return "interrupting collection"; }
        };
        int[] factories = {0};
        var nested = conditionPerWait("nested check", () -> {
            factories[0]++;
            return (String value) -> satisfied(value);
        });
        assertInstanceOf(InterruptedException.class, thrown(quantify.apply(nested), actual));
        assertTrue(currentThread().isInterrupted());
        assertEquals(0, factories[0]);
    }

    private static List<Named<Function<AwaitCondition.InputCondition<String>, PreservingCondition<Collection<? extends String>>>>> quantifiers() {
        return List.of(named("all", CollectionConditions::all), named("any", CollectionConditions::any), named("none", CollectionConditions::none));
    }

    @ParameterizedTest
    @MethodSource("getterFailures")
    void getterFailuresKeepTheirOriginalCauseWhenTheyAlsoInterrupt(Throwable cause) {
        var actual = new ArrayList<>(List.of("ready")) {
            @Override public Iterator<String> iterator() {
                currentThread().interrupt();
                return rethrow(cause);
            }
        };
        assertSame(cause, thrown(singleElement, actual));
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Throwable> getterFailures() {
        return Stream.of(new IllegalStateException("getter failed"), new InternalError("fatal getter"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interruptedExtractionSkipsTheNestedFactory(boolean sequence) {
        int[] factories = {0};
        var check = conditionPerWait("nested check", () -> {
            factories[0]++;
            return (String value) -> satisfied(value);
        });
        var selected = valueFor("key", check).because("value must be readable");
        var actual = new SignallingMap("getValue");
        assertInstanceOf(InterruptedException.class, thrown(sequence ? captured(selected, selected) : selected, actual));
        assertEquals(0, factories[0]);
        assertEquals("key", selected.references().getFirst().value());
        assertEquals("value must be readable", selected.explanation());
        assertTrue(currentThread().isInterrupted());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void interruptedNarrowingKeepsTheConditionDefinition(boolean sequence) {
        var narrowed = valueFor("key", instanceOf(String.class)).because("value must be readable");
        var actual = new SignallingMap("getValue");
        assertInstanceOf(InterruptedException.class, thrown(sequence ? captured(narrowed, narrowed) : narrowed, actual));
        assertEquals("key", narrowed.references().getFirst().value());
        assertEquals("value must be readable", narrowed.explanation());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void interruptionDuringExtractionStillAllowsTheComparator() {
        var actual = new SignallingList("secondNext");
        int[] comparisons = {0};
        assertInterrupted(() -> bind(sorted((left, right) -> {
            comparisons[0]++;
            return 0;
        })).probe(actual), "next", actual.calls);
        assertEquals(1, comparisons[0]);
    }

    @ParameterizedTest
    @MethodSource({"collectionTraversalInterruptions", "mapTraversalInterruptions", "mapSizeAndViewInterruptions", "selectionInterruptions"})
    void interruptionStopsAtTheConditionBoundary(Executable scenario) throws Throwable {
        scenario.execute();
    }

    private static Stream<Named<Executable>> collectionTraversalInterruptions() {
        var waits = List.<Named<AwaitCondition>>of(
                named("all(predicate)", all(value -> true)),
                named("any(predicate)", any(value -> true)),
                named("none(predicate)", none(value -> false)),
                named("firstElement(predicate)", firstElement(value -> true)),
                named("firstElement", firstElement),
                named("lastElement", lastElement),
                named("lastElement(predicate)", lastElement(value -> true)),
                named("elementAt", elementAt(0)),
                named("singleElement(condition)", singleElement(matches(value -> true))),
                named("contains", contains("ready")),
                named("doesNotContain", doesNotContain("absent")),
                named("containsExactly", containsExactly(List.of("ready", "other"))),
                named("containsExactlyInAnyOrder", containsExactlyInAnyOrder(List.of("other", "ready"))),
                named("containsOnly", containsOnly(List.of("ready", "other"))),
                named("containsSubsequence", containsSubsequence(List.of("ready"))),
                named("hasNoDuplicates", hasNoDuplicates),
                named("sorted", sorted((left, right) -> 0)));
        return Stream.of("iterator", "hasNext", "next").flatMap(boundary -> waits.stream().map(wait ->
                named("collection / " + wait.getName() + " / " + boundary, () -> {
                    var actual = new SignallingList(boundary);

                    assertInterrupted(() -> bind(wait.getPayload()).probe(actual), boundary, actual.calls);

                })));
    }

    private static Stream<Named<Executable>> mapTraversalInterruptions() {
        var traversal = List.of("entrySet", "iterator", "hasNext", "next");
        var keyRead = List.of("entrySet", "iterator", "hasNext", "next", "getKey");
        var valueRead = List.of("entrySet", "iterator", "hasNext", "next", "getKey", "getValue");
        Map<Named<AwaitCondition>, List<String>> waits = Map.ofEntries(
                Map.entry(named("singleEntry", singleEntry), traversal),
                Map.entry(named("singleEntry(condition)", singleEntry(matches(value -> true))), traversal),
                Map.entry(named("valueFor", valueFor("key")), valueRead),
                Map.entry(named("entryFor", entryFor("key")), keyRead),
                Map.entry(named("allEntries", allEntries((key, value) -> true)), valueRead),
                Map.entry(named("hasEntry(predicate)", hasEntry((key, value) -> true)), valueRead),
                Map.entry(named("hasNoEntry(predicate)", hasNoEntry((key, value) -> false)), valueRead),
                Map.entry(named("hasEntry", hasEntry("key", "ready")), valueRead),
                Map.entry(named("containsAllEntries", containsAllEntries(Map.of("key", "ready"))), valueRead),
                Map.entry(named("containsExactlyEntries", containsExactlyEntries(Map.of("key", "ready"))), valueRead));
        return waits.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(boundary ->
                named("map / " + entry.getKey().getName() + " / " + boundary, () -> {
                    var actual = new SignallingMap(boundary);

                    assertInterrupted(() -> bind(entry.getKey().getPayload()).probe(actual), boundary, actual.calls);

                })));
    }

    private static Stream<Named<Executable>> mapSizeAndViewInterruptions() {
        Map<Named<AwaitCondition>, String> cases = Map.ofEntries(
                Map.entry(named("singleEntry", singleEntry), "entrySet"),
                Map.entry(named("containsExactlyEntries", containsExactlyEntries(Map.of("key", "ready"))), "size"),
                Map.entry(named("hasSize", MapConditions.hasSize(1)), "size"),
                Map.entry(named("allKeys", allKeys(value -> true)), "keySet"),
                Map.entry(named("containsKey", containsKey("key")), "keySet"),
                Map.entry(named("containsOnlyKeys", containsOnlyKeys(singletonList("key"))), "keySet"),
                Map.entry(named("allValues", allValues(value -> true)), "values"),
                Map.entry(named("containsValue", containsValue("ready")), "values"));
        return cases.entrySet().stream().map(entry -> named("map view / " + entry.getKey().getName() + " / " + entry.getValue(), () -> {
            var actual = new SignallingMap(entry.getValue());
            assertInterrupted(() -> bind(entry.getKey().getPayload()).probe(actual), entry.getValue(), actual.calls);
        }));
    }

    private static Stream<Named<Executable>> selectionInterruptions() {
        Map<Named<AwaitCondition>, String> cases = Map.ofEntries(
                Map.entry(named("firstElement", firstElement), "iterator"),
                Map.entry(named("lastElement", lastElement), "reversed"),
                Map.entry(named("lastElement(predicate)", lastElement(value -> true)), "reversed"),
                Map.entry(named("elementAt", elementAt(0)), "iterator"),
                Map.entry(named("containsSequence", containsSequence(List.of("ready"))), "size"),
                Map.entry(named("startsWith", startsWith(List.of("ready"))), "size"),
                Map.entry(named("endsWith", endsWith(List.of("other"))), "size"),
                Map.entry(named("hasSize", hasSize(2)), "size"));
        return cases.entrySet().stream().map(entry -> named("selection / " + entry.getKey().getName() + " / " + entry.getValue(), () -> {
            var actual = new SignallingList(entry.getValue());
            assertInterrupted(() -> bind(entry.getKey().getPayload()).probe(actual), entry.getValue(), actual.calls);
        }));
    }

    private static void assertInterrupted(Executable probe, String boundary, List<String> calls) {
        var failure = assertThrows(Throwable.class, probe);
        assertInstanceOf(InterruptedException.class,
                failure instanceof io.github.gromoff97.awium.execution.ProbeFailure nested ? nested.getCause() : failure);
        assertThat(calls).contains(boundary);
        assertTrue(currentThread().isInterrupted());
    }

    private static Consumer<String> interruptAt(String boundary) {
        Consumer<String> signal = mock();
        doAnswer(ignored -> { currentThread().interrupt(); return null; }).when(signal).accept(boundary);
        return signal;
    }

    private static final class SignallingMap extends AbstractMap<String, String> {
        private final Consumer<String> signal;
        private final List<String> calls = new ArrayList<>();

        SignallingMap(String boundary) { signal = interruptAt(boundary); }

        private void called(String method) {
            calls.add(method);
            signal.accept(method);
        }

        @Override
        public int size() { called("size"); return 1; }

        @Override
        public Set<Entry<String, String>> entrySet() {
            return view("entrySet", new SimpleImmutableEntry<>("key", "ready") {
                @Override
                public String getKey() { called("getKey"); return super.getKey(); }

                @Override
                public String getValue() { called("getValue"); return super.getValue(); }
            });
        }

        @Override
        public Set<String> keySet() { return view("keySet", "key"); }

        @Override
        public Set<String> values() { return view("values", "ready"); }

        private <VALUE> Set<VALUE> view(String method, VALUE value) {
            called(method);
            return new AbstractSet<>() {
                @Override
                public int size() { return 1; }

                @Override
                public Iterator<VALUE> iterator() {
                    called("iterator");
                    return new Iterator<>() {
                        private boolean consumed;

                        @Override
                        public boolean hasNext() { called("hasNext"); return !consumed; }

                        @Override
                        public VALUE next() { called("next"); consumed = true; return value; }
                    };
                }
            };
        }

        @Override
        public String toString() { return "signalling map"; }
    }

    private static final class SignallingList extends ArrayList<String> {
        private final Consumer<String> signal;
        private final List<String> calls = new ArrayList<>();

        SignallingList(String boundary) {
            super(List.of("ready", "other"));
            signal = interruptAt(boundary);
        }

        private void called(String method) {
            calls.add(method);
            signal.accept(method);
        }

        @Override
        public int size() { called("size"); return super.size(); }

        @Override
        public List<String> reversed() { called("reversed"); return this; }

        @Override
        public Iterator<String> iterator() {
            called("iterator");
            Iterator<String> delegate = super.iterator();
            Runnable onNext = mock();
            doNothing().doAnswer(ignored -> { signal.accept("secondNext"); return null; }).doNothing().when(onNext).run();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { called("hasNext"); return delegate.hasNext(); }

                @Override
                public String next() {
                    called("next");
                    onNext.run();
                    return delegate.next();
                }
            };
        }

        @Override
        public String toString() { return "signalling list"; }
    }
}
