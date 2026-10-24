package io.github.gromoff97.awium;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.ConditionTestRuntime.throwUnchecked;
import static io.github.gromoff97.awium.Conditions.matches;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class ContainerInterruptionTest {

    @Test
    void getterFailuresKeepTheirOriginalCauseWhenTheyAlsoInterrupt() {
        for (Throwable cause : List.of(new IllegalStateException("getter failed"), new InternalError("fatal getter"))) {
            for (boolean throwing : List.of(false, true)) {
                var actual = new ArrayList<>(List.of("ready")) {
                    @Override
                    public Iterator<String> iterator() {
                        Thread.currentThread().interrupt();
                        return throwUnchecked(cause);
                    }

                    @Override
                    public String toString() { return "failing collection"; }
                };
                try {
                    var waiting = await(() -> actual);
                    if (throwing) {
                        assertSame(cause, FailureTaxonomyTest.assertFailure(INTERRUPTED, () -> waiting.until(single)).getCause());
                    } else {
                        var failure = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(single)).failure();
                        assertEquals(INTERRUPTED, failure.reason());
                        assertSame(cause, failure.cause());
                    }
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void interruptionDuringExtractionStillAllowsTheComparator() {
        var actual = new SignallingList("secondNext");
        int[] comparisons = {0};
        assertInterrupted(() -> await(() -> actual).tryUntil(sorted((left, right) -> {
            comparisons[0]++;
            return 0;
        })), "next", actual.calls);
        assertEquals(1, comparisons[0]);
    }

    @Test
    void interruptionsWithinCollectionTraversalsStopAtTheConditionBoundary() {
        List<Function<Await<SignallingList, String, Void>, AwaitResult<?, ?>>> waits = List.of(
                waiting -> waiting.tryUntil(all(value -> true)),
                waiting -> waiting.tryUntil(any(value -> true)),
                waiting -> waiting.tryUntil(none(value -> false)),
                waiting -> waiting.tryUntil(first(value -> true)),
                waiting -> waiting.tryUntil(first),
                waiting -> waiting.tryUntil(last),
                waiting -> waiting.tryUntil(last(value -> true)),
                waiting -> waiting.tryUntil(element(0)),
                waiting -> waiting.tryUntil(single(matches(value -> true))),
                waiting -> waiting.tryUntil(contains("ready")),
                waiting -> waiting.tryUntil(doesNotContain("absent")),
                waiting -> waiting.tryUntil(containsExactly(List.of("ready", "other"))),
                waiting -> waiting.tryUntil(containsExactlyInAnyOrder(List.of("other", "ready"))),
                waiting -> waiting.tryUntil(containsOnly(List.of("ready", "other"))),
                waiting -> waiting.tryUntil(containsSubsequence("ready")),
                waiting -> waiting.tryUntil(hasNoDuplicates),
                waiting -> waiting.tryUntil(sorted((left, right) -> 0)));
        for (String boundary : List.of("iterator", "hasNext", "next")) {
            for (var wait : waits) {
                var actual = new SignallingList(boundary);
                var time = new FakeTime(0);
                var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
                assertInterrupted(() -> wait.apply(waiting), boundary, actual.calls);
                assertTrue(time.parkRequests.isEmpty());
            }
        }
    }

    @Test
    void interruptionsWithinMapTraversalsStopAtTheConditionBoundary() {
        List<Function<Await<SignallingMap, Map.Entry<String, String>, String>, AwaitResult<?, ?>>> waits = List.of(
                waiting -> waiting.tryUntil(MapConditions.singleEntry),
                waiting -> waiting.tryUntil(MapConditions.singleEntry(matches(value -> true))),
                waiting -> waiting.tryUntil(MapConditions.valueFor("key")),
                waiting -> waiting.tryUntil(MapConditions.onlyValueFor("key")),
                waiting -> waiting.tryUntil(MapConditions.entryFor("key")),
                waiting -> waiting.tryUntil(MapConditions.allEntries((key, value) -> true)),
                waiting -> waiting.tryUntil(MapConditions.anyEntry((key, value) -> true)),
                waiting -> waiting.tryUntil(MapConditions.noEntry((key, value) -> false)),
                waiting -> waiting.tryUntil(MapConditions.containsEntries("key", "ready")),
                waiting -> waiting.tryUntil(MapConditions.containsEntries(Map.of("key", "ready"))),
                waiting -> waiting.tryUntil(MapConditions.containsExactlyEntries(Map.of("key", "ready"))));
        for (String boundary : List.of("entrySet", "iterator", "hasNext", "next", "getKey", "getValue")) {
            for (int index = 0; index < waits.size(); index++) {
                // Selecting an entry does not inspect its key.
                if (boundary.equals("getKey") && index < 2) continue;
                if (boundary.equals("getValue") && (index < 2 || index == 4)) continue;
                var actual = new SignallingMap(boundary);
                var time = new FakeTime(0);
                var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
                var wait = waits.get(index);
                assertInterrupted(() -> wait.apply(waiting), boundary, actual.calls);
                assertTrue(time.parkRequests.isEmpty());
            }
        }
    }

    @Test
    void interruptionsDuringMapSizeAndViewsAreDetectedAfterTheCheck() {
        List<Function<SignallingMap, AwaitResult<?, ?>>> waits = List.of(
                actual -> await(() -> actual).tryUntil(MapConditions.singleEntry),
                actual -> await(() -> actual).tryUntil(MapConditions.onlyValueFor("key")),
                actual -> await(() -> actual).tryUntil(MapConditions.containsExactlyEntries(Map.of("key", "ready"))),
                actual -> await(() -> actual).tryUntil(MapConditions.size(1)),
                actual -> await(() -> actual).tryUntil(MapConditions.allKeys(value -> true)),
                actual -> await(() -> actual).tryUntil(MapConditions.containsKey("key")),
                actual -> await(() -> actual).tryUntil(MapConditions.containsOnlyKeys(java.util.Collections.singletonList("key"))),
                actual -> await(() -> actual).tryUntil(MapConditions.allValues(value -> true)),
                actual -> await(() -> actual).tryUntil(MapConditions.containsValue("ready")));
        List<String> boundaries = List.of("entrySet", "entrySet", "size", "size", "keySet", "keySet", "keySet", "values", "values");
        for (int index = 0; index < waits.size(); index++) {
            var actual = new SignallingMap(boundaries.get(index));
            var wait = waits.get(index);
            assertInterrupted(() -> wait.apply(actual), boundaries.get(index), actual.calls);
        }
    }

    @Test
    void interruptionsDuringSelectionAreDetectedAfterTheCheck() {
        List<Function<SignallingList, AwaitResult<?, ?>>> waits = List.of(
                actual -> await(() -> actual).tryUntil(first),
                actual -> await(() -> actual).tryUntil(last),
                actual -> await(() -> actual).tryUntil(last(value -> true)),
                actual -> await(() -> actual).tryUntil(element(0)),
                actual -> await(() -> actual).tryUntil(containsSequence("ready")),
                actual -> await(() -> actual).tryUntil(startsWith("ready")),
                actual -> await(() -> actual).tryUntil(endsWith("other")),
                actual -> await(() -> actual).tryUntil(size(2)));
        List<String> boundaries = List.of("iterator", "reversed", "reversed", "iterator", "size", "size", "size", "size");
        for (int index = 0; index < waits.size(); index++) {
            var actual = new SignallingList(boundaries.get(index));
            var wait = waits.get(index);
            assertInterrupted(() -> wait.apply(actual), boundaries.get(index), actual.calls);
        }
    }

    private static void assertInterrupted(Supplier<AwaitResult<?, ?>> wait, String boundary, List<String> calls) {
        try {
            var result = assertInstanceOf(AwaitResult.Failed.class, wait.get());
            assertEquals(1, result.totalAttempts());
            var failure = result.failure();
            assertEquals(INTERRUPTED, failure.reason(), calls.toString());
            assertInstanceOf(InterruptedException.class, failure.cause());
            assertTrue(calls.contains(boundary), calls.toString());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

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
