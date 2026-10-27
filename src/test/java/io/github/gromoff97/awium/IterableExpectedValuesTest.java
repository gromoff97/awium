package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

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
    void invalidExpectationsFailBeforePollingAndEmptyExactExpectationsStillWork() {
        Iterable<String> empty = List.of();
        Iterable<String> absent = null;
        assertThrows(IllegalArgumentException.class, () -> containsAll(empty));
        assertThrows(IllegalArgumentException.class, () -> in(empty));
        assertThrows(IllegalArgumentException.class, () -> MapConditions.containsAllKeys(empty));
        assertThrows(NullPointerException.class, () -> containsAll(absent));
        assertThrows(NullPointerException.class, () -> notIn(absent));
        List<String> actual = List.of();
        assertSame(actual, waiting(() -> actual).until(containsOnly(empty)));
        assertSame(actual, waiting(() -> actual).until(containsExactly(empty)));

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
        var before = waiting.tryUntil(condition);
        mutable.clear();
        mutable.add("changed");
        var after = waiting.tryUntil(condition);

        assertEquals(1, expected.iterations);
        assertEquals(before.getClass(), after.getClass());
        if (before instanceof AwaitResult.Satisfied<?, ?> success) {
            ACTUAL value = waiting.until(condition);
            assertSame(actual, value);
            assertSame(actual, success.result());
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
