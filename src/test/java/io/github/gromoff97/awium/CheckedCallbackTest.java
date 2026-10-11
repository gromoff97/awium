package io.github.gromoff97.awium;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
                    "condition factory evaluator", () -> waiting.until(conditionFactory("checked", () -> actual -> { throw cause; })),
                    "preserving factory evaluator", () -> waiting.until(preservingFactory("checked", () -> actual -> { throw cause; })),
                    "condition factory creation", () -> waiting.until(conditionFactory("checked", () -> { throw cause; })),
                    "preserving factory creation", () -> waiting.until(preservingFactory("checked", () -> { throw cause; })));

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
        return conditionFactory("checked receipt", () -> actual -> { throw cause; });
    }
}
