package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.Conditions.*;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.MapConditions.singleEntry;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class CheckedCallbackTest {

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void checkedMethodReferencesPreserveAndTransformResults() {
        var time = new FakeTime(0);
        var waiting = await(() -> "ready").usingTime(time, time);
        assertEquals("ready", waiting.until(asserted(CheckedCallbackTest::verify)));
        assertEquals(5, waiting.until(yields(CheckedCallbackTest::length)));
        assertEquals("ready", waiting.until(condition("checked evaluation", CheckedCallbackTest::evaluate)));
        assertEquals("ready", waiting.until(preserving("checked evaluation", CheckedCallbackTest::evaluate)));
    }

    @Test
    void checkedFailuresStopEveryCallbackFormOnceWithTheOriginalCause() {
        for (Exception cause : List.of(new IOException("offline"), new InterruptedException("cancelled"))) {
            var time = new FakeTime(0);
            int[] calls = {0};
            Source<String> source = () -> "value" + ++calls[0];
            var waiting = await(source).usingTime(time, time);
            Map<String, Executable> callbacks = Map.of(
                    "asserted", () -> waiting.until(asserted(actual -> { throw cause; })),
                    "yields", () -> waiting.until(yields(actual -> { throw cause; })),
                    "condition", () -> waiting.until(condition("checked", actual -> { throw cause; })),
                    "preserving", () -> waiting.until(preserving("checked", actual -> { throw cause; })),
                    "condition factory evaluator", () -> waiting.until(condition("checked", () -> actual -> { throw cause; })),
                    "preserving factory evaluator", () -> waiting.until(preserving("checked", () -> actual -> { throw cause; })),
                    "condition factory creation", () -> waiting.until(condition("checked", () -> { throw cause; })),
                    "preserving factory creation", () -> waiting.until(preserving("checked", () -> { throw cause; })));

            for (var callback : callbacks.entrySet()) {
                calls[0] = 0;
                try {
                    Throwable failure = cause instanceof InterruptedException
                            ? assertFailure(INTERRUPTED, callback.getValue())
                            : assertFailure(CONDITION_FAILED, callback.getValue());
                    assertSame(cause, failure.getCause(), callback.getKey());
                    assertEquals(cause instanceof InterruptedException, Thread.currentThread().isInterrupted());
                    assertEquals(1, calls[0], callback.getKey());
                    assertEquals(List.of(), time.parkRequests);
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void checkedCallbacksStillRetryAssertionFailures() {
        var time = new FakeTime(0);
        int[] calls = {0};
        int result = await(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1)).until(asserted(CheckedCallbackTest::verifySecond));
        assertEquals(2, result);
        assertEquals(2, calls[0]);
        assertEquals(List.of(1L), time.parkRequests);
    }

    @Test
    void interruptionDuringFactoryCreationSkipsItsEvaluator() {
        List<Function<Callable<CheckedFunction<String, ConditionResult<String>>>, AwaitResult<?, ?>>> waits = List.of(
                factory -> await(() -> "ready").tryUntil(condition("lazy", factory)),
                factory -> await(() -> "ready").tryUntil(preserving("lazy", factory)),
                factory -> await(() -> Optional.of("ready")).tryUntil(hasValue(condition("lazy", factory))),
                factory -> await(() -> Optional.of("ready")).tryUntil(hasValue(preserving("lazy", factory))),
                factory -> await(() -> "ready").tryUntil(condition("lazy", factory), yields(String::trim)),
                factory -> await(() -> "ready").tryUntil(preserving("lazy", factory), matches(value -> true)));
        for (var wait : waits) {
            int[] factoryCalls = {0};
            int[] evaluatorCalls = {0};
            try {
                var result = wait.apply(() -> {
                    factoryCalls[0]++;
                    Thread.currentThread().interrupt();
                    return actual -> { evaluatorCalls[0]++; return satisfied(actual); };
                });
                var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                assertEquals(INTERRUPTED, failure.failure().reason());
                assertInstanceOf(InterruptedException.class, failure.failure().cause());
                assertEquals(1, factoryCalls[0]);
                assertEquals(0, evaluatorCalls[0]);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptionDuringValueExtractionSkipsTheNestedFactoryAndEvaluator() {
        var entry = new AbstractMap.SimpleImmutableEntry<String, String>("key", "ready") {
            @Override
            public String getValue() {
                Thread.currentThread().interrupt();
                return super.getValue();
            }
        };
        var actual = new ProbeContainers.EntryMap<String, String>(List.of(entry));
        int[] factoryCalls = {0};
        int[] evaluatorCalls = {0};
        var result = await(() -> actual).tryUntil(valueFor("key", condition("nested", () -> {
            factoryCalls[0]++;
            return value -> { evaluatorCalls[0]++; return satisfied(value); };
        })));

        var failure = assertInstanceOf(AwaitResult.Failed.class, result);
        assertEquals(INTERRUPTED, failure.failure().reason());
        assertInstanceOf(InterruptedException.class, failure.failure().cause());
        assertEquals(0, factoryCalls[0]);
        assertEquals(0, evaluatorCalls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void interruptionDuringSizeExtractionSkipsTheIterator() {
        for (boolean throwing : List.of(false, true)) {
            int[] iteratorCalls = {0};
            var actual = new ArrayList<>(List.of("ready")) {
                @Override
                public int size() {
                    Thread.currentThread().interrupt();
                    return super.size();
                }

                @Override
                public Iterator<String> iterator() {
                    iteratorCalls[0]++;
                    Thread.interrupted();
                    return super.iterator();
                }

                @Override
                public String toString() { return "signalling collection"; }
            };
            try {
                var waiting = await(() -> actual);
                Throwable cause = throwing
                        ? assertFailure(INTERRUPTED, () -> waiting.until(single)).getCause()
                        : assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(single)).failure().cause();
                assertInstanceOf(InterruptedException.class, cause);
                assertEquals(0, iteratorCalls[0]);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptionDuringIndexedExtractionSkipsThePredicate() {
        var actual = new ArrayList<>(List.of("ready")) {
            @Override
            public String get(int index) {
                Thread.currentThread().interrupt();
                return super.get(index);
            }
        };
        int[] calls = {0};
        var result = await(() -> actual).tryUntil(CollectionConditions.element(0, value -> {
            calls[0]++;
            return true;
        }));
        var failure = assertInstanceOf(AwaitResult.Failed.class, result);
        assertEquals(INTERRUPTED, failure.failure().reason());
        assertInstanceOf(InterruptedException.class, failure.failure().cause());
        assertEquals(0, calls[0]);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void interruptionDuringIterationSkipsPredicatesAndComparators() {
        var actual = new ArrayList<>(List.of("first", "second")) {
            @Override
            public Iterator<String> iterator() {
                Iterator<String> delegate = super.iterator();
                return new Iterator<>() {
                    private boolean signalled;

                    @Override
                    public boolean hasNext() { return delegate.hasNext(); }

                    @Override
                    public String next() {
                        if (!signalled) {
                            signalled = true;
                            Thread.currentThread().interrupt();
                        }
                        return delegate.next();
                    }
                };
            }

            @Override
            public String toString() { return "signalling iterator"; }
        };
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        List<Function<Predicate<String>, AwaitResult<?, ?>>> waits = List.of(
                predicate -> waiting.tryUntil(CollectionConditions.first(predicate)),
                predicate -> waiting.tryUntil(CollectionConditions.all(predicate)),
                predicate -> waiting.tryUntil(CollectionConditions.any(predicate)),
                predicate -> waiting.tryUntil(CollectionConditions.none(predicate)),
                predicate -> waiting.tryUntil(CollectionConditions.sorted((left, right) -> {
                    predicate.test(left);
                    return 0;
                })));
        for (var wait : waits) {
            int[] calls = {0};
            try {
                var result = wait.apply(value -> {
                    calls[0]++;
                    Thread.interrupted();
                    return true;
                });
                var failure = assertInstanceOf(AwaitResult.Failed.class, result).failure();
                assertEquals(INTERRUPTED, failure.reason());
                assertInstanceOf(InterruptedException.class, failure.cause());
                assertEquals(0, calls[0]);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptionDuringKeyExtractionSkipsEqualityCallbacks() {
        int[] comparisons = {0};
        Object key = new Object() {
            @Override
            public boolean equals(Object other) {
                comparisons[0]++;
                Thread.interrupted();
                return true;
            }

            @Override
            public int hashCode() { throw new AssertionError("hashCode must not be called"); }
        };
        var entry = new AbstractMap.SimpleImmutableEntry<Object, String>(key, "ready") {
            @Override
            public Object getKey() {
                Thread.currentThread().interrupt();
                return super.getKey();
            }
        };
        var actual = new ProbeContainers.EntryMap<Object, String>(List.of(entry));
        var expectedKey = new Object();
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                () -> waiting.tryUntil(valueFor(expectedKey)),
                () -> waiting.tryUntil(valueFor(expectedKey, equalTo("ready"))),
                () -> waiting.tryUntil(MapConditions.onlyValueFor(expectedKey)),
                () -> waiting.tryUntil(MapConditions.containsEntries(expectedKey, "ready")),
                () -> waiting.tryUntil(MapConditions.doesNotContainEntries(expectedKey, "ready")));
        for (var wait : waits) {
            comparisons[0] = 0;
            try {
                var failure = assertInstanceOf(AwaitResult.Failed.class, wait.get()).failure();
                assertEquals(INTERRUPTED, failure.reason());
                assertInstanceOf(InterruptedException.class, failure.cause());
                assertEquals(0, comparisons[0]);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptionDuringEntryIterationSkipsEveryEntryGetter() {
        int[] reads = {0, 0};
        var entry = new AbstractMap.SimpleImmutableEntry<String, String>("key", "ready") {
            @Override
            public String getKey() {
                reads[0]++;
                Thread.interrupted();
                return super.getKey();
            }

            @Override
            public String getValue() {
                reads[1]++;
                Thread.interrupted();
                return super.getValue();
            }
        };
        var entries = new ArrayList<Map.Entry<String, String>>(List.of(entry)) {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                var delegate = super.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() { return delegate.hasNext(); }

                    @Override
                    public Map.Entry<String, String> next() {
                        Thread.currentThread().interrupt();
                        return delegate.next();
                    }
                };
            }
        };
        var actual = new ProbeContainers.EntryMap<>(entries);
        var expected = Map.of("key", "ready");
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));
        List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                () -> waiting.tryUntil(MapConditions.containsEntries(expected)),
                () -> waiting.tryUntil(MapConditions.doesNotContainAllEntries(expected)),
                () -> waiting.tryUntil(MapConditions.containsExactlyEntries(expected)),
                () -> waiting.tryUntil(MapConditions.doesNotContainExactlyEntries(expected)),
                () -> waiting.tryUntil(MapConditions.containsAnyEntries(expected)),
                () -> waiting.tryUntil(MapConditions.doesNotContainEntries(expected)));
        for (var wait : waits) {
            reads[0] = reads[1] = 0;
            try {
                var failure = assertInstanceOf(AwaitResult.Failed.class, wait.get()).failure();
                assertEquals(INTERRUPTED, failure.reason());
                assertInstanceOf(InterruptedException.class, failure.cause());
                assertArrayEquals(new int[]{0, 0}, reads);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptionDuringEntryExtractionSkipsEveryEntryPredicate() {
        for (boolean key : List.of(false, true)) {
            int[] valueReads = {0};
            var entry = new AbstractMap.SimpleImmutableEntry<String, String>("key", "ready") {
                @Override
                public String getKey() {
                    if (key) Thread.currentThread().interrupt();
                    return super.getKey();
                }

                @Override
                public String getValue() {
                    valueReads[0]++;
                    if (!key) Thread.currentThread().interrupt();
                    return super.getValue();
                }
            };
            var actual = new ProbeContainers.EntryMap<String, String>(List.of(entry));
            List<Function<BiPredicate<String, String>, AwaitResult<?, ?>>> waits = List.of(
                    predicate -> await(() -> actual).tryUntil(MapConditions.allEntries(predicate)),
                    predicate -> await(() -> actual).tryUntil(MapConditions.anyEntry(predicate)),
                    predicate -> await(() -> actual).tryUntil(MapConditions.noEntry(predicate)));
            for (var wait : waits) {
                int[] calls = {0};
                valueReads[0] = 0;
                try {
                    var result = wait.apply((name, value) -> { calls[0]++; return true; });
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                    assertEquals(INTERRUPTED, failure.failure().reason());
                    assertInstanceOf(InterruptedException.class, failure.failure().cause());
                    assertEquals(0, calls[0]);
                    assertEquals(key ? 0 : 1, valueReads[0]);
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void interruptionDuringKeyComparisonSkipsFurtherEntryExtraction() {
        for (boolean matching : List.of(false, true)) {
            int[] keyReads = {0};
            int[] valueReads = {0};
            var actualKey = new Object() {
                @Override
                public boolean equals(Object other) {
                    Thread.currentThread().interrupt();
                    return matching;
                }

                @Override
                public int hashCode() { throw new AssertionError("hashCode must not be called"); }
            };
            var entry = new AbstractMap.SimpleImmutableEntry<Object, String>(actualKey, "ready") {
                @Override
                public Object getKey() { keyReads[0]++; return super.getKey(); }

                @Override
                public String getValue() { valueReads[0]++; return super.getValue(); }
            };
            var second = new AbstractMap.SimpleImmutableEntry<Object, String>(new Object(), "other") {
                @Override
                public Object getKey() { keyReads[0]++; return super.getKey(); }

                @Override
                public String getValue() { valueReads[0]++; return super.getValue(); }
            };
            var actual = new ProbeContainers.EntryMap<Object, String>(List.of(entry, second));
            var only = new ProbeContainers.EntryMap<Object, String>(List.of(entry));
            Object expectedKey = new Object();
            List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                    () -> await(() -> actual).tryUntil(valueFor(expectedKey)),
                    () -> await(() -> actual).tryUntil(valueFor(expectedKey, equalTo("ready"))),
                    () -> await(() -> only).tryUntil(MapConditions.onlyValueFor(expectedKey)),
                    () -> await(() -> actual).tryUntil(MapConditions.containsEntries(expectedKey, "ready")),
                    () -> await(() -> actual).tryUntil(MapConditions.doesNotContainEntries(expectedKey, "ready")),
                    () -> await(() -> actual).tryUntil(MapConditions.containsEntries(Map.of(expectedKey, "ready"))));
            for (var wait : waits) {
                keyReads[0] = 0;
                valueReads[0] = 0;
                try {
                    var failure = assertInstanceOf(AwaitResult.Failed.class, wait.get()).failure();
                    assertEquals(INTERRUPTED, failure.reason());
                    assertInstanceOf(InterruptedException.class, failure.cause());
                    assertEquals(1, keyReads[0]);
                    assertEquals(0, valueReads[0]);
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void nestedAndCapturedCheckedFailuresKeepTheirContextAndExplanation() {
        var cause = new IOException("offline");
        var nested = failing(cause).because("receipt is needed");
        var time = new FakeTime(0);

        var optional = await(() -> Optional.of("ready")).usingTime(time, time).tryUntil(hasValue(nested));
        var map = await(() -> Map.of("key", "ready")).usingTime(time, time).tryUntil(valueFor("key", nested));
        var sequence = await(() -> "ready").usingTime(time, time).every(ofNanos(1)).tryUntil(yields(String::trim), nested);
        for (AwaitResult<?, ?> result : List.of(optional, map, sequence)) {
            var failure = assertInstanceOf(AwaitResult.Failed.class, result);
            assertSame(cause, failure.failure().cause());
            assertTrue(failure.failure().message().contains("receipt is needed"));
        }
        var outcome = assertInstanceOf(ConditionResult.Uncontrolled.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, sequence.attempts().getLast().outcome()).evaluation());
        var context = assertInstanceOf(ConditionResult.Context.Sequence.class, outcome.context());
        assertEquals(1, context.capturedStages());
        assertEquals(2, context.evaluatedStageNumber());
    }

    @Test
    void builtInTraversalsStopBeforeTheNextCallbackAfterInterruption() {
        List<Function<Predicate<Integer>, AwaitResult<?, ?>>> waits = List.of(
                predicate -> await(() -> List.of(1, 2)).tryUntil(single(matches(predicate))),
                predicate -> await(() -> Map.of("a", 1, "b", 2)).tryUntil(singleEntry(matches(
                        (Map.Entry<String, Integer> entry) -> predicate.test(entry.getValue())))),
                predicate -> await(() -> List.of(1, 2)).tryUntil(CollectionConditions.first(predicate)),
                predicate -> await(() -> List.of(1, 2)).tryUntil(CollectionConditions.last(predicate)),
                predicate -> await(() -> List.of(1, 2)).tryUntil(CollectionConditions.all(predicate)),
                predicate -> await(() -> List.of(1, 2)).tryUntil(CollectionConditions.any(predicate)),
                predicate -> await(() -> List.of(1, 2)).tryUntil(CollectionConditions.none(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.allKeys(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.anyKey(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.noKey(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.allValues(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.anyValue(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.noValue(predicate)),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.allEntries((key, value) -> predicate.test(value))),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.anyEntry((key, value) -> predicate.test(value))),
                predicate -> await(() -> Map.of(1, 1, 2, 2)).tryUntil(MapConditions.noEntry((key, value) -> predicate.test(value))));
        for (var wait : waits) {
            for (boolean matching : List.of(false, true)) {
                var calls = new AtomicInteger();
                try {
                    var result = wait.apply(value -> {
                        calls.incrementAndGet();
                        Thread.currentThread().interrupt();
                        return matching;
                    });
                    var failure = assertInstanceOf(AwaitResult.Failed.class, result);
                    assertInstanceOf(InterruptedException.class, failure.failure().cause());
                    assertTrue(Thread.currentThread().isInterrupted());
                    assertEquals(1, calls.get());
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void signalledCallbacksStopBeforeTheIteratorIsReadAgain() {
        int[] laterReads = {0};
        var calls = new AtomicInteger();
        var actual = new ArrayList<>(List.of("first", "second")) {
            @Override
            public Iterator<String> iterator() {
                Iterator<String> delegate = super.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        if (Thread.currentThread().isInterrupted()) {
                            laterReads[0]++;
                            throw new IllegalStateException("iterator called after interruption");
                        }
                        return delegate.hasNext();
                    }

                    @Override
                    public String next() { return delegate.next(); }
                };
            }

            @Override
            public String toString() { return "interruptible list"; }
        };
        Predicate<String> matching = value -> {
            calls.incrementAndGet();
            Thread.currentThread().interrupt();
            return true;
        };
        Predicate<String> mismatching = matching.negate();
        List<Supplier<AwaitResult<?, ?>>> waits = List.of(
                () -> await(() -> actual).tryUntil(single(matches(matching))),
                () -> await(() -> actual).tryUntil(single(yields((String value) -> {
                    matching.test(value);
                    return value.length();
                }))),
                () -> await(() -> actual).tryUntil(CollectionConditions.any(mismatching)),
                () -> await(() -> actual).tryUntil(CollectionConditions.all(matching)),
                () -> await(() -> actual).tryUntil(CollectionConditions.none(mismatching)),
                () -> await(() -> actual).tryUntil(CollectionConditions.first(mismatching)),
                () -> await(() -> actual).tryUntil(CollectionConditions.sorted((left, right) -> {
                    matching.test(left);
                    return 0;
                })));
        for (var wait : waits) {
            calls.set(0);
            laterReads[0] = 0;
            try {
                var failure = assertInstanceOf(AwaitResult.Failed.class, wait.get()).failure();
                assertEquals(INTERRUPTED, failure.reason());
                assertInstanceOf(InterruptedException.class, failure.cause());
                assertEquals(1, calls.get());
                assertEquals(0, laterReads[0]);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void comparisonCallbacksStopBeforeAnotherComparisonAfterInterruption() {
        record Value(int id, boolean interrupts, AtomicInteger calls) implements Comparable<Value> {
            private void compared() {
                if (interrupts) {
                    calls.incrementAndGet();
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public int compareTo(Value other) {
                compared();
                return Integer.compare(id, other.id);
            }

            @Override
            public boolean equals(Object other) {
                compared();
                return other instanceof Value value && id == value.id;
            }

            @Override
            public int hashCode() {
                return id;
            }
        }
        var calls = new AtomicInteger();
        var first = new Value(0, true, calls);
        var second = new Value(1, true, calls);
        var expectedFirst = new Value(0, false, calls);
        var expectedSecond = new Value(1, false, calls);
        var missing = new Value(2, false, calls);
        var map = new IdentityHashMap<Value, Integer>();
        map.put(first, 1);
        map.put(second, 2);
        Map<String, Supplier<AwaitResult<?, ?>>> operations = Map.of(
                "sorting", () -> await(() -> List.of(1, 2, 3)).tryUntil(CollectionConditions.sorted((left, right) -> {
                    calls.incrementAndGet();
                    Thread.currentThread().interrupt();
                    return 0;
                })),
                "containment", () -> await(() -> List.of(first)).tryUntil(CollectionConditions.contains(expectedSecond, missing)),
                "negative containment", () -> await(() -> List.of(first)).tryUntil(CollectionConditions.doesNotContainAll(expectedSecond, missing)),
                "exact content", () -> await(() -> List.of(first, second)).tryUntil(CollectionConditions.containsExactly(expectedFirst, expectedSecond)),
                "array equality", () -> await(() -> new Value[] {first, second}).tryUntil(equalTo(new Value[] {expectedFirst, expectedSecond})),
                "map key", () -> await(() -> map).tryUntil(valueFor(missing)),
                "range", () -> await(() -> first).tryUntil(between(expectedFirst, missing)));
        for (var operation : operations.entrySet()) {
            calls.set(0);
            try {
                var failed = assertInstanceOf(AwaitResult.Failed.class, operation.getValue().get());
                assertEquals(INTERRUPTED, failed.failure().reason(), operation.getKey());
                assertInstanceOf(InterruptedException.class, failed.failure().cause());
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(1, calls.get(), operation.getKey());
            } finally {
                Thread.interrupted();
            }
        }
    }

    private static void verify(String actual) throws IOException {
        assertEquals("ready", actual);
    }

    private static int length(String actual) throws IOException {
        return actual.length();
    }

    private static ConditionResult<String> evaluate(String actual) throws IOException {
        return satisfied(actual);
    }

    private static void verifySecond(int actual) throws IOException {
        assertEquals(2, actual);
    }

    private static Condition<String, String> failing(IOException cause) {
        return condition("checked receipt", () -> actual -> { throw cause; });
    }
}
