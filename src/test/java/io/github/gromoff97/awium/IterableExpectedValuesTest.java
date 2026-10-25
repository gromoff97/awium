package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
                MapConditions::containsKeys, MapConditions::doesNotContainKeys, MapConditions::containsOnlyKeys,
                MapConditions::containsValues, MapConditions::doesNotContainValues);

        for (var factory : factories) {
            assertStableSnapshot(factory, List.of("a", "b"), Map.of("a", "a", "b", "b"));
        }
    }

    @Test
    void candidateSnapshotsRetainNullsAndArraysAsWholeValues() {
        var values = new ArrayList<>(asList(null, "ready"));
        var expected = new Once<>(values);
        var allowed = in(expected).because("allowed values");
        values.clear();
        assertEquals(5, await(() -> "ready").until(allowed).length());
        String missing = null;
        assertNull(await(() -> missing).until(allowed));
        assertEquals(1, expected.iterations);

        int[] element = {1, 2};
        Iterable<int[]> arrays = List.of(element);
        assertEquals(2, await(() -> new int[] {1, 2}).until(in(arrays).because("whole array")).length);
        assertEquals(1, await(() -> new int[] {3}).until(notIn(arrays).because("whole array")).length);
        assertEquals(2, await(() -> List.of(element)).until(containsOnly(arrays)).getFirst().length);

        Iterable<List<String>> lists = List.of(List.of("ready"));
        assertEquals(5, await(() -> List.of(List.of("ready"))).until(containsOnly(lists)).getFirst().getFirst().length());
    }

    @Test
    void invalidExpectationsFailBeforePollingAndEmptyExactExpectationsStillWork() {
        Iterable<String> empty = List.of();
        Iterable<String> absent = null;
        assertThrows(IllegalArgumentException.class, () -> containsAll(empty));
        assertThrows(IllegalArgumentException.class, () -> in(empty));
        assertThrows(IllegalArgumentException.class, () -> MapConditions.containsKeys(empty));
        assertThrows(NullPointerException.class, () -> containsAll(absent));
        assertThrows(NullPointerException.class, () -> notIn(absent));
        List<String> actual = List.of();
        assertSame(actual, await(() -> actual).until(containsOnly(empty).because("empty")));
        assertSame(actual, await(() -> actual).until(containsExactly(empty).because("empty")));

        int[] reads = {0};
        var cause = new IllegalStateException("expected iterator failed");
        Iterable<String> broken = () -> { throw cause; };
        assertSame(cause, assertThrows(IllegalStateException.class, () ->
                await(() -> { reads[0]++; return List.of("ready"); }).until(containsAll(broken))));
        assertEquals(0, reads[0]);
    }

    private static <ACTUAL> void assertStableSnapshot(
            Function<Iterable<String>, PreservingCondition<? super ACTUAL>> factory,
            List<String> expectedValues, ACTUAL actual) {
        var mutable = new ArrayList<>(expectedValues);
        var expected = new Once<>(mutable);
        var condition = factory.apply(expected).because("frozen expectation");
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
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
