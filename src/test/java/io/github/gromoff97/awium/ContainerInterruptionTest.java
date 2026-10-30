package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ContainerInterruptionTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void interruptionBeforeTheFirstNestedCheckSkipsItsFactory() {
        var actual = new AbstractList<String>() {
            @Override public int size() { return 1; }
            @Override public String get(int index) {
                java.util.Objects.checkIndex(index, size());
                Thread.currentThread().interrupt();
                return "ready";
            }
            @Override public String toString() { return "interrupting collection"; }
        };
        int[] factories = {0};
        var nested = Conditions.conditionPerWait("nested check", () -> {
            factories[0]++;
            return (String value) -> ConditionResult.satisfied();
        });
        for (var condition : List.of(all(nested), any(nested), none(nested))) {
            try {
                var result = tryAwait(() -> actual).until(condition);
                assertEquals(INTERRUPTED, failure(result).reason());
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(0, factories[0]);
            } finally {
                Thread.interrupted();
            }
        }
    }

    @ParameterizedTest(name = "fatal={0}, throwing={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void getterFailuresKeepTheirOriginalCauseWhenTheyAlsoInterrupt(boolean fatal, boolean throwing) {
        Throwable cause = fatal ? new InternalError("fatal getter") : new IllegalStateException("getter failed");
        var actual = new ArrayList<>(List.of("ready")) {
            @Override
            public Iterator<String> iterator() {
                Thread.currentThread().interrupt();
                return rethrow(cause);
            }

            @Override
            public String toString() { return "failing collection"; }
        };
        var waiting = await(() -> actual);
        if (throwing) {
            assertSame(cause, FailureTaxonomyTest.assertFailure(INTERRUPTED, () -> waiting.until(singleElement)).getCause());
        } else {
            var failure = failure(diagnostic(waiting).until(singleElement));
            assertEquals(INTERRUPTED, failure.reason());
            assertSame(cause, failure.cause());
        }
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"selected,false", "narrowed,true", "captured selected,false", "captured narrowed,true"})
    void interruptedExtractionKeepsTheDefinitionWithoutStartingTheNestedCheck(String form, boolean useNarrowed) {
        int[] factories = {0};
        var check = Conditions.conditionPerWait("nested check", () -> {
            factories[0]++;
            return (String value) -> ConditionResult.satisfied();
        });
        var selected = MapConditions.valueFor("key", check).because("value must be readable");
        var narrowed = MapConditions.valueFor("key", Conditions.instanceOf(String.class)).because("value must be readable");
        var actual = new SignallingMap("getValue");
        var waiting = await(() -> actual);
        var result = switch (form) {
            case "selected" -> diagnostic(waiting).until(selected);
            case "narrowed" -> diagnostic(waiting).until(narrowed);
            case "captured selected" -> diagnostic(waiting).until(captured(selected, selected));
            case "captured narrowed" -> diagnostic(waiting).until(captured(narrowed, narrowed));
            default -> throw new AssertionError(form);
        };
        var failure = failure(result);
        assertThat(attempts(result)).isNotEmpty();
        var condition = result.conditions().getFirst().condition();

        assertEquals(INTERRUPTED, failure.reason());
        assertEquals(0, factories[0]);
        assertSame(useNarrowed ? narrowed : selected, condition);
        assertEquals("key", condition.references().getFirst().value());
        assertEquals("value must be readable", condition.explanation());
        assertTrue(Thread.currentThread().isInterrupted());
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

    @Test
    void interruptionsWithinCollectionTraversalsStopAtTheConditionBoundary() {
        List<Function<SelectionAwait<SignallingList, String>, AwaitResult<?, ?>>> waits = List.of(
                waiting -> diagnostic(waiting).until(all(value -> true)),
                waiting -> diagnostic(waiting).until(any(value -> true)),
                waiting -> diagnostic(waiting).until(none(value -> false)),
                waiting -> diagnostic(waiting).until(firstElement(value -> true)),
                waiting -> diagnostic(waiting).until(firstElement),
                waiting -> diagnostic(waiting).until(lastElement),
                waiting -> diagnostic(waiting).until(lastElement(value -> true)),
                waiting -> diagnostic(waiting).until(elementAt(0)),
                waiting -> diagnostic(waiting).until(singleElement(matches(value -> true))),
                waiting -> diagnostic(waiting).until(contains("ready")),
                waiting -> diagnostic(waiting).until(doesNotContain("absent")),
                waiting -> diagnostic(waiting).until(containsExactly(List.of("ready", "other"))),
                waiting -> diagnostic(waiting).until(containsExactlyInAnyOrder(List.of("other", "ready"))),
                waiting -> diagnostic(waiting).until(containsOnly(List.of("ready", "other"))),
                waiting -> diagnostic(waiting).until(containsSubsequence(List.of("ready"))),
                waiting -> diagnostic(waiting).until(hasNoDuplicates),
                waiting -> diagnostic(waiting).until(sorted((left, right) -> 0)));
        for (String boundary : List.of("iterator", "hasNext", "next")) {
            for (var wait : waits) {
                var actual = new SignallingList(boundary);
                var time = new FakeTime(0);
                var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
                assertInterrupted(() -> wait.apply(waiting), boundary, actual.calls);
                assertThat(time.parkRequests).isEmpty();
            }
        }
    }

    @Test
    void interruptionsWithinMapTraversalsStopAtTheConditionBoundary() {
        List<Function<MapAwait<SignallingMap, Map.Entry<String, String>, String>, AwaitResult<?, ?>>> waits = List.of(
                waiting -> diagnostic(waiting).until(MapConditions.singleEntry),
                waiting -> diagnostic(waiting).until(MapConditions.singleEntry(matches(value -> true))),
                waiting -> diagnostic(waiting).until(MapConditions.valueFor("key")),
                waiting -> diagnostic(waiting).until(MapConditions.entryFor("key")),
                waiting -> diagnostic(waiting).until(MapConditions.allEntries((key, value) -> true)),
                waiting -> diagnostic(waiting).until(MapConditions.hasEntry((key, value) -> true)),
                waiting -> diagnostic(waiting).until(MapConditions.hasNoEntry((key, value) -> false)),
                waiting -> diagnostic(waiting).until(MapConditions.hasEntry("key", "ready")),
                waiting -> diagnostic(waiting).until(MapConditions.containsAllEntries(Map.of("key", "ready"))),
                waiting -> diagnostic(waiting).until(MapConditions.containsExactlyEntries(Map.of("key", "ready"))));
        for (String boundary : List.of("entrySet", "iterator", "hasNext", "next", "getKey", "getValue")) {
            for (int index = 0; index < waits.size(); index++) {
                // Selecting an entry does not inspect its key.
                if (boundary.equals("getKey") && index < 2) continue;
                // entryFor selects the entry without reading its value.
                if (boundary.equals("getValue") && (index < 2 || index == 3)) continue;
                var actual = new SignallingMap(boundary);
                var time = new FakeTime(0);
                var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
                var wait = waits.get(index);
                assertInterrupted(() -> wait.apply(waiting), boundary, actual.calls);
                assertThat(time.parkRequests).isEmpty();
            }
        }
    }

    @Test
    void interruptionsDuringMapSizeAndViewsAreDetectedAfterTheCheck() {
        List<BoundaryCase<SignallingMap>> cases = List.of(
                new BoundaryCase<>("entrySet", actual -> tryAwait(() -> actual).until(MapConditions.singleEntry)),
                new BoundaryCase<>("size", actual -> tryAwait(() -> actual).until(MapConditions.containsExactlyEntries(Map.of("key", "ready")))),
                new BoundaryCase<>("size", actual -> tryAwait(() -> actual).until(MapConditions.hasSize(1))),
                new BoundaryCase<>("keySet", actual -> tryAwait(() -> actual).until(MapConditions.allKeys(value -> true))),
                new BoundaryCase<>("keySet", actual -> tryAwait(() -> actual).until(MapConditions.containsKey("key"))),
                new BoundaryCase<>("keySet", actual -> tryAwait(() -> actual).until(MapConditions.containsOnlyKeys(java.util.Collections.singletonList("key")))),
                new BoundaryCase<>("values", actual -> tryAwait(() -> actual).until(MapConditions.allValues(value -> true))),
                new BoundaryCase<>("values", actual -> tryAwait(() -> actual).until(MapConditions.containsValue("ready"))));
        for (var testCase : cases) {
            var actual = new SignallingMap(testCase.boundary());
            assertInterrupted(() -> testCase.check().apply(actual), testCase.boundary(), actual.calls);
        }
    }

    @Test
    void interruptionsDuringSelectionAreDetectedAfterTheCheck() {
        List<BoundaryCase<SignallingList>> cases = List.of(
                new BoundaryCase<>("iterator", actual -> tryAwait(() -> actual).until(firstElement)),
                new BoundaryCase<>("reversed", actual -> tryAwait(() -> actual).until(lastElement)),
                new BoundaryCase<>("reversed", actual -> tryAwait(() -> actual).until(lastElement(value -> true))),
                new BoundaryCase<>("iterator", actual -> tryAwait(() -> actual).until(elementAt(0))),
                new BoundaryCase<>("size", actual -> tryAwait(() -> actual).until(containsSequence(List.of("ready")))),
                new BoundaryCase<>("size", actual -> tryAwait(() -> actual).until(startsWith(List.of("ready")))),
                new BoundaryCase<>("size", actual -> tryAwait(() -> actual).until(endsWith(List.of("other")))),
                new BoundaryCase<>("size", actual -> tryAwait(() -> actual).until(hasSize(2))));
        for (var testCase : cases) {
            var actual = new SignallingList(testCase.boundary());
            assertInterrupted(() -> testCase.check().apply(actual), testCase.boundary(), actual.calls);
        }
    }

    private static void assertInterrupted(Supplier<AwaitResult<?, ?>> wait, String boundary, List<String> calls) {
        try {
            var outcome = wait.get();
            var failure = failure(outcome);
            assertEquals(1, attempts(outcome).size());
            assertEquals(INTERRUPTED, failure.reason(), calls.toString());
            assertInstanceOf(InterruptedException.class, failure.cause());
            assertThat(calls).contains(boundary);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private record BoundaryCase<ACTUAL>(String boundary,
            Function<ACTUAL, AwaitResult<?, ?>> check) {}

    private static final class SignallingMap extends AbstractMap<String, String> {
        private final String boundary;
        private final List<String> calls = new ArrayList<>();

        SignallingMap(String boundary) { this.boundary = boundary; }

        private void called(String method) {
            calls.add(method);
            if (method.equals(boundary)) Thread.currentThread().interrupt();
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
        private final String boundary;
        private final List<String> calls = new ArrayList<>();

        SignallingList(String boundary) {
            super(List.of("ready", "other"));
            this.boundary = boundary;
        }

        private void called(String method) {
            calls.add(method);
            if (method.equals(boundary)) Thread.currentThread().interrupt();
        }

        @Override
        public int size() { called("size"); return super.size(); }

        @Override
        public List<String> reversed() { called("reversed"); return this; }

        @Override
        public Iterator<String> iterator() {
            called("iterator");
            Iterator<String> delegate = super.iterator();
            return new Iterator<>() {
                private int nextCalls;

                @Override
                public boolean hasNext() { called("hasNext"); return delegate.hasNext(); }

                @Override
                public String next() {
                    called("next");
                    if (++nextCalls == 2 && boundary.equals("secondNext")) Thread.currentThread().interrupt();
                    return delegate.next();
                }
            };
        }

        @Override
        public String toString() { return "signalling list"; }
    }
}
