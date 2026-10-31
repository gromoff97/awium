package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.allEntries;
import static io.github.gromoff97.awium.MapConditions.allKeys;
import static io.github.gromoff97.awium.MapConditions.allValues;
import static io.github.gromoff97.awium.MapConditions.containsAllEntries;
import static io.github.gromoff97.awium.MapConditions.containsExactlyEntries;
import static io.github.gromoff97.awium.MapConditions.containsKey;
import static io.github.gromoff97.awium.MapConditions.containsOnlyKeys;
import static io.github.gromoff97.awium.MapConditions.containsValue;
import static io.github.gromoff97.awium.MapConditions.entryFor;
import static io.github.gromoff97.awium.MapConditions.hasEntry;
import static io.github.gromoff97.awium.MapConditions.hasNoEntry;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static java.util.Collections.singletonList;
import static java.util.Map.Entry;
import static java.util.Objects.checkIndex;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
            Function<Condition.InputCondition<String>, PreservingCondition<Collection<? extends String>>> quantify) {
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
        var result = tryAwait(() -> actual).until(quantify.apply(nested));
        failure(INTERRUPTED, result);
        assertTrue(currentThread().isInterrupted());
        assertEquals(0, factories[0]);
    }

    private static List<Named<Function<Condition.InputCondition<String>, PreservingCondition<Collection<? extends String>>>>> quantifiers() {
        return List.of(named("all", CollectionConditions::all), named("any", CollectionConditions::any), named("none", CollectionConditions::none));
    }

    @ParameterizedTest
    @MethodSource("getterFailures")
    void getterFailuresKeepTheirOriginalCauseWhenTheyAlsoInterrupt(Throwable cause, BinaryOperator<Runnable> terminal) {
        var actual = new ArrayList<>(List.of("ready")) {
            @Override
            public Iterator<String> iterator() {
                currentThread().interrupt();
                return rethrow(cause);
            }

            @Override
            public String toString() { return "failing collection"; }
        };
        var waiting = await(() -> actual);
        terminal.apply(
                () -> assertSame(cause, assertFailure(INTERRUPTED, () -> waiting.until(singleElement)).getCause()),
                () -> {
                    var failure = failure(INTERRUPTED, diagnostic(waiting).until(singleElement));
                    assertSame(cause, failure.cause());
                }).run();
        assertTrue(currentThread().isInterrupted());
    }

    private static Stream<Arguments> getterFailures() {
        var terminals = List.<Named<BinaryOperator<Runnable>>>of(
                named("await", (thrown, captured) -> thrown), named("tryAwait", (thrown, captured) -> captured));
        return Stream.of(new IllegalStateException("getter failed"), new InternalError("fatal getter"))
                .flatMap(cause -> terminals.stream().map(terminal -> arguments(cause, terminal)));
    }

    @ParameterizedTest
    @MethodSource("extractionForms")
    void interruptedExtractionSkipsTheNestedFactory(BinaryOperator<Supplier<AwaitResult<?, ?>>> form) {
        int[] factories = {0};
        var check = conditionPerWait("nested check", () -> {
            factories[0]++;
            return (String value) -> satisfied(value);
        });
        var selected = valueFor("key", check).because("value must be readable");
        var actual = new SignallingMap("getValue");
        var waiting = await(() -> actual);
        var result = form.apply(() -> diagnostic(waiting).until(selected), () -> diagnostic(waiting).until(captured(selected, selected))).get();
        assertInterruptedExtraction(result, selected);
        assertEquals(0, factories[0]);
    }

    @ParameterizedTest
    @MethodSource("extractionForms")
    void interruptedNarrowingKeepsTheConditionDefinition(BinaryOperator<Supplier<AwaitResult<?, ?>>> form) {
        var narrowed = valueFor("key", instanceOf(String.class)).because("value must be readable");
        var actual = new SignallingMap("getValue");
        var waiting = await(() -> actual);
        var result = form.apply(() -> diagnostic(waiting).until(narrowed), () -> diagnostic(waiting).until(captured(narrowed, narrowed))).get();
        assertInterruptedExtraction(result, narrowed);
    }

    private static List<Named<BinaryOperator<Supplier<AwaitResult<?, ?>>>>> extractionForms() {
        return List.of(named("single condition", (single, captured) -> single), named("captured", (single, captured) -> captured));
    }

    private static void assertInterruptedExtraction(AwaitResult<?, ?> result, Condition expected) {
        assertThat(attempts(result)).isNotEmpty();
        var condition = result.conditions().getFirst().condition();
        failure(INTERRUPTED, result);
        assertSame(expected, condition);
        assertEquals("key", condition.references().getFirst().value());
        assertEquals("value must be readable", condition.explanation());
        assertTrue(currentThread().isInterrupted());
    }

    @Test
    void interruptionDuringExtractionStillAllowsTheComparator() {
        var actual = new SignallingList("secondNext");
        int[] comparisons = {0};
        assertInterrupted(() -> tryAwait(() -> actual).until(sorted((left, right) -> {
            comparisons[0]++;
            return 0;
        })), "next", actual.calls);
        assertEquals(1, comparisons[0]);
    }

    @ParameterizedTest
    @MethodSource({"collectionTraversalInterruptions", "mapTraversalInterruptions", "mapSizeAndViewInterruptions", "selectionInterruptions"})
    void interruptionStopsAtTheConditionBoundary(Executable scenario) throws Throwable {
        scenario.execute();
    }

    private static Stream<Named<Executable>> collectionTraversalInterruptions() {
        var waits = List.<Named<Function<SelectionAwait<SignallingList, String>, AwaitResult<?, ?>>>>of(
                named("all(predicate)", waiting -> diagnostic(waiting).until(all(value -> true))),
                named("any(predicate)", waiting -> diagnostic(waiting).until(any(value -> true))),
                named("none(predicate)", waiting -> diagnostic(waiting).until(none(value -> false))),
                named("firstElement(predicate)", waiting -> diagnostic(waiting).until(firstElement(value -> true))),
                named("firstElement", waiting -> diagnostic(waiting).until(firstElement)),
                named("lastElement", waiting -> diagnostic(waiting).until(lastElement)),
                named("lastElement(predicate)", waiting -> diagnostic(waiting).until(lastElement(value -> true))),
                named("elementAt", waiting -> diagnostic(waiting).until(elementAt(0))),
                named("singleElement(condition)", waiting -> diagnostic(waiting).until(singleElement(matches(value -> true)))),
                named("contains", waiting -> diagnostic(waiting).until(contains("ready"))),
                named("doesNotContain", waiting -> diagnostic(waiting).until(doesNotContain("absent"))),
                named("containsExactly", waiting -> diagnostic(waiting).until(containsExactly(List.of("ready", "other")))),
                named("containsExactlyInAnyOrder", waiting -> diagnostic(waiting).until(containsExactlyInAnyOrder(List.of("other", "ready")))),
                named("containsOnly", waiting -> diagnostic(waiting).until(containsOnly(List.of("ready", "other")))),
                named("containsSubsequence", waiting -> diagnostic(waiting).until(containsSubsequence(List.of("ready")))),
                named("hasNoDuplicates", waiting -> diagnostic(waiting).until(hasNoDuplicates)),
                named("sorted", waiting -> diagnostic(waiting).until(sorted((left, right) -> 0))));
        return Stream.of("iterator", "hasNext", "next").flatMap(boundary -> waits.stream().map(wait ->
                named("collection / " + wait.getName() + " / " + boundary, () -> {
                    var actual = new SignallingList(boundary);
                    var time = new FakeTime(0);
                    var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
                    assertInterrupted(() -> wait.getPayload().apply(waiting), boundary, actual.calls);
                    assertThat(time.parkRequests).isEmpty();
                })));
    }

    private static Stream<Named<Executable>> mapTraversalInterruptions() {
        var traversal = List.of("entrySet", "iterator", "hasNext", "next");
        var keyRead = List.of("entrySet", "iterator", "hasNext", "next", "getKey");
        var valueRead = List.of("entrySet", "iterator", "hasNext", "next", "getKey", "getValue");
        Map<Named<Function<MapAwait<SignallingMap, Entry<String, String>, String>, AwaitResult<?, ?>>>, List<String>> waits = Map.ofEntries(
                Map.entry(named("singleEntry", waiting -> diagnostic(waiting).until(singleEntry)), traversal),
                Map.entry(named("singleEntry(condition)", waiting -> diagnostic(waiting).until(singleEntry(matches(value -> true)))), traversal),
                Map.entry(named("valueFor", waiting -> diagnostic(waiting).until(valueFor("key"))), valueRead),
                Map.entry(named("entryFor", waiting -> diagnostic(waiting).until(entryFor("key"))), keyRead),
                Map.entry(named("allEntries", waiting -> diagnostic(waiting).until(allEntries((key, value) -> true))), valueRead),
                Map.entry(named("hasEntry(predicate)", waiting -> diagnostic(waiting).until(hasEntry((key, value) -> true))), valueRead),
                Map.entry(named("hasNoEntry(predicate)", waiting -> diagnostic(waiting).until(hasNoEntry((key, value) -> false))), valueRead),
                Map.entry(named("hasEntry", waiting -> diagnostic(waiting).until(hasEntry("key", "ready"))), valueRead),
                Map.entry(named("containsAllEntries", waiting -> diagnostic(waiting).until(containsAllEntries(Map.of("key", "ready")))), valueRead),
                Map.entry(named("containsExactlyEntries", waiting -> diagnostic(waiting).until(containsExactlyEntries(Map.of("key", "ready")))), valueRead));
        return waits.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(boundary ->
                named("map / " + entry.getKey().getName() + " / " + boundary, () -> {
                    var actual = new SignallingMap(boundary);
                    var time = new FakeTime(0);
                    var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
                    assertInterrupted(() -> entry.getKey().getPayload().apply(waiting), boundary, actual.calls);
                    assertThat(time.parkRequests).isEmpty();
                })));
    }

    private static Stream<Named<Executable>> mapSizeAndViewInterruptions() {
        Map<Named<Function<SignallingMap, AwaitResult<?, ?>>>, String> cases = Map.ofEntries(
                Map.entry(named("singleEntry", actual -> tryAwait(() -> actual).until(singleEntry)), "entrySet"),
                Map.entry(named("containsExactlyEntries", actual -> tryAwait(() -> actual).until(containsExactlyEntries(Map.of("key", "ready")))), "size"),
                Map.entry(named("hasSize", actual -> tryAwait(() -> actual).until(MapConditions.hasSize(1))), "size"),
                Map.entry(named("allKeys", actual -> tryAwait(() -> actual).until(allKeys(value -> true))), "keySet"),
                Map.entry(named("containsKey", actual -> tryAwait(() -> actual).until(containsKey("key"))), "keySet"),
                Map.entry(named("containsOnlyKeys", actual -> tryAwait(() -> actual).until(containsOnlyKeys(singletonList("key")))), "keySet"),
                Map.entry(named("allValues", actual -> tryAwait(() -> actual).until(allValues(value -> true))), "values"),
                Map.entry(named("containsValue", actual -> tryAwait(() -> actual).until(containsValue("ready"))), "values"));
        return cases.entrySet().stream().map(entry -> named("map view / " + entry.getKey().getName() + " / " + entry.getValue(), () -> {
            var actual = new SignallingMap(entry.getValue());
            assertInterrupted(() -> entry.getKey().getPayload().apply(actual), entry.getValue(), actual.calls);
        }));
    }

    private static Stream<Named<Executable>> selectionInterruptions() {
        Map<Named<Function<SignallingList, AwaitResult<?, ?>>>, String> cases = Map.ofEntries(
                Map.entry(named("firstElement", actual -> tryAwait(() -> actual).until(firstElement)), "iterator"),
                Map.entry(named("lastElement", actual -> tryAwait(() -> actual).until(lastElement)), "reversed"),
                Map.entry(named("lastElement(predicate)", actual -> tryAwait(() -> actual).until(lastElement(value -> true))), "reversed"),
                Map.entry(named("elementAt", actual -> tryAwait(() -> actual).until(elementAt(0))), "iterator"),
                Map.entry(named("containsSequence", actual -> tryAwait(() -> actual).until(containsSequence(List.of("ready")))), "size"),
                Map.entry(named("startsWith", actual -> tryAwait(() -> actual).until(startsWith(List.of("ready")))), "size"),
                Map.entry(named("endsWith", actual -> tryAwait(() -> actual).until(endsWith(List.of("other")))), "size"),
                Map.entry(named("hasSize", actual -> tryAwait(() -> actual).until(hasSize(2))), "size"));
        return cases.entrySet().stream().map(entry -> named("selection / " + entry.getKey().getName() + " / " + entry.getValue(), () -> {
            var actual = new SignallingList(entry.getValue());
            assertInterrupted(() -> entry.getKey().getPayload().apply(actual), entry.getValue(), actual.calls);
        }));
    }

    private static void assertInterrupted(Supplier<AwaitResult<?, ?>> wait, String boundary, List<String> calls) {
        var outcome = wait.get();
        var failure = failure(outcome);
        assertEquals(1, attempts(outcome).size());
        assertEquals(INTERRUPTED, failure.reason(), calls.toString());
        assertInstanceOf(InterruptedException.class, failure.cause());
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
