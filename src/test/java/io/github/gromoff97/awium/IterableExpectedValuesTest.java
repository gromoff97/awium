package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.in;
import static io.github.gromoff97.awium.Conditions.notIn;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class IterableExpectedValuesTest {

    @Test
    void collectionFactoriesSnapshotExpectedIterablesOnce() {
        List<Function<Iterable<String>, PreservingCondition<? super List<String>>>> factories = List.of(
                CollectionConditions::containsAll, CollectionConditions::doesNotContainAny,
                CollectionConditions::doesNotContainAll, CollectionConditions::containsAny,
                CollectionConditions::containsExactly, CollectionConditions::doesNotContainExactly,
                CollectionConditions::containsExactlyInAnyOrder, CollectionConditions::doesNotContainExactlyInAnyOrder,
                CollectionConditions::containsOnly, CollectionConditions::subsetOf,
                CollectionConditions::startsWith, CollectionConditions::endsWith,
                CollectionConditions::containsSequence, CollectionConditions::doesNotContainSequence,
                CollectionConditions::containsSubsequence, CollectionConditions::doesNotContainSubsequence);

        for (var factory : factories) {
            assertStableSnapshot(factory, asList("a", null, "b"), asList("a", null, "b"));
        }
    }

    @Test
    void mapKeyAndValueFactoriesSnapshotExpectedIterablesOnce() {
        List<Function<Iterable<String>, PreservingCondition<? super Map<String, String>>>> factories = List.of(
                MapConditions::containsAllKeys, MapConditions::doesNotContainAnyKeys, MapConditions::containsOnlyKeys,
                MapConditions::containsAllValues, MapConditions::doesNotContainAnyValues);

        for (var factory : factories) {
            assertStableSnapshot(factory, List.of("a", "b"), Map.of("a", "a", "b", "b"));
        }
    }

    @Test
    void textFactoriesSnapshotExpectedIterablesOnce() {
        List<Function<Iterable<String>, PreservingCondition<? super StringBuilder>>> factories = List.of(
                StringConditions::containsAll, StringConditions::doesNotContainAny);
        for (var factory : factories) {
            assertStableSnapshot(factory, List.of("a", "b"), new StringBuilder("abc"));
            assertStableSnapshot(factory, List.of("x", "y"), new StringBuilder("abc"));
        }
    }

    @Test
    void expectedSnapshotsReadSplittableInputsOnTheCallingThread() {
        var caller = Thread.currentThread();
        var elements = IntStream.range(0, 2048).mapToObj(Integer::toString).toList();
        int[] reads = {0};
        var expected = new Iterable<String>() {
            @Override public Iterator<String> iterator() { return elements.iterator(); }
            @Override public Spliterator<String> spliterator() {
                return elements.parallelStream().map(value -> {
                    assertSame(caller, Thread.currentThread(), "expected values must be read on the caller thread");
                    reads[0]++;
                    return value;
                }).spliterator();
            }
        };
        List<Function<Iterable<String>, Condition>> factories = List.of(
                CollectionConditions::containsExactly, MapConditions::containsAllKeys,
                Conditions::in, StringConditions::containsAll, StringConditions::doesNotContainAny);
        for (var factory : factories) {
            reads[0] = 0;
            var condition = factory.apply(expected);
            assertEquals(elements.size(), reads[0]);
            assertEquals(elements, condition.references().getFirst().value());
        }
    }

    @Test
    void candidateSnapshotsRetainNullsAndArraysAsWholeValues() {
        var values = new ArrayList<>(asList(null, "ready"));
        var expected = new Once<>(values);
        var allowed = in(expected);
        values.clear();
        assertEquals(5, waiting(() -> "ready").until(allowed).length());
        String missing = null;
        assertNull(waiting(() -> missing).until(allowed));
        assertEquals(1, expected.iterations);

        int[] element = {1, 2};
        Iterable<int[]> arrays = List.of(element);
        assertEquals(2, waiting(() -> new int[] {1, 2}).until(in(arrays)).length);
        assertEquals(1, waiting(() -> new int[] {3}).until(notIn(arrays)).length);
        assertEquals(2, waiting(() -> List.of(element)).until(containsOnly(arrays)).getFirst().length);

        Iterable<List<String>> lists = List.of(List.of("ready"));
        assertEquals(5, waiting(() -> List.of(List.of("ready"))).until(containsOnly(lists)).getFirst().getFirst().length());
    }

    @Test
    void invalidExpectationsFailBeforePollingAndEmptyMembershipExpectationsWork() {
        Iterable<String> empty = List.of();
        Iterable<String> absent = null;
        assertThrows(NullPointerException.class, () -> containsAll(absent));
        assertThrows(NullPointerException.class, () -> notIn(absent));
        assertEquals("ready", waiting(() -> "ready").until(notIn(empty)));
        List<String> actual = List.of();
        assertSame(actual, waiting(() -> actual).until(containsAll(empty)));
        assertSame(actual, waiting(() -> actual).until(containsOnly(empty)));
        assertSame(actual, waiting(() -> actual).until(containsExactly(empty)));
        var entries = Map.of("key", "value");
        assertSame(entries, waiting(() -> entries).until(MapConditions.containsAllKeys(empty)));

        int[] reads = {0};
        var cause = new IllegalStateException("expected iterator failed");
        Iterable<String> broken = () -> { throw cause; };
        assertSame(cause, assertThrows(IllegalStateException.class, () ->
                waiting(() -> { reads[0]++; return List.of("ready"); }).until(containsAll(broken))));
        assertEquals(0, reads[0]);
    }

    private static <ACTUAL> void assertStableSnapshot(
            Function<Iterable<String>, PreservingCondition<? super ACTUAL>> factory,
            List<String> expectedValues, ACTUAL actual) {
        var mutable = new ArrayList<>(expectedValues);
        var expected = new Once<>(mutable);
        var condition = factory.apply(expected);
        var waiting = waiting(() -> actual);
        var before = diagnostic(waiting).until(condition);
        mutable.clear();
        mutable.add("changed");
        var after = diagnostic(waiting).until(condition);

        assertEquals(1, expected.iterations);
        assertEquals(before.getClass(), after.getClass());
        if (before instanceof AwaitResult.Satisfied(var ignoredHistory, var observed)) {
            ACTUAL value = waiting.until(condition);
            assertSame(actual, value);
            assertSame(actual, observed);
        } else {
            var original = assertInstanceOf(AwaitResult.Failed.class, before).failure();
            var current = assertInstanceOf(AwaitResult.Failed.class, after).failure();
            assertEquals(original.message(), current.message());
            assertFalse(current.message().contains("changed"));
            assertThrows(AwaitAssertionError.class, () -> waiting.until(condition));
        }
        assertEquals(1, expected.iterations);
    }

    private static <OBSERVED> ValueAwait<OBSERVED> waiting(Callable<OBSERVED> source) {
        var time = new FakeTime(0);
        return await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
    }

    private static final class Once<ELEMENT> implements Iterable<ELEMENT> {
        private final List<ELEMENT> values;
        private int iterations;

        private Once(List<ELEMENT> values) { this.values = values; }

        @Override
        public Iterator<ELEMENT> iterator() {
            if (++iterations != 1) throw new AssertionError("expected values were traversed again");
            return values.iterator();
        }
    }
}
