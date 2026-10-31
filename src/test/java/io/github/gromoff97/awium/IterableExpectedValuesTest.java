package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitAssertionError;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.CollectionConditions.*;
import static io.github.gromoff97.awium.Conditions.in;
import static io.github.gromoff97.awium.Conditions.notIn;
import static java.time.Duration.ofNanos;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static io.github.gromoff97.awium.MapConditions.containsAllKeys;
import static io.github.gromoff97.awium.result.AwaitResult.Satisfied;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static java.lang.Thread.currentThread;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

class IterableExpectedValuesTest {

    @ParameterizedTest
    @MethodSource("collectionFactories")
    void collectionFactoriesSnapshotExpectedIterablesOnce(Function<Iterable<String>, PreservingCondition<? super List<String>>> factory) {
        assertStableSnapshot(factory, asList("a", null, "b"), asList("a", null, "b"));
    }

    @ParameterizedTest
    @MethodSource("mapFactories")
    void mapKeyAndValueFactoriesSnapshotExpectedIterablesOnce(Function<Iterable<String>, PreservingCondition<? super Map<String, String>>> factory) {
        assertStableSnapshot(factory, List.of("a", "b"), Map.of("a", "a", "b", "b"));
    }

    @ParameterizedTest
    @MethodSource("textFactories")
    void textFactoriesSnapshotExpectedIterablesOnce(Function<Iterable<String>, PreservingCondition<? super StringBuilder>> factory, List<String> expected) {
        assertStableSnapshot(factory, expected, new StringBuilder("abc"));
    }

    @ParameterizedTest
    @MethodSource("splittableFactories")
    void expectedSnapshotsReadSplittableInputsOnTheCallingThread(Function<Iterable<String>, Condition> factory) {
        var caller = currentThread();
        var elements = range(0, 2048).mapToObj(Integer::toString).toList();
        int[] reads = {0};
        var expected = new Iterable<String>() {
            @Override public Iterator<String> iterator() { return elements.iterator(); }
            @Override public Spliterator<String> spliterator() {
                return elements.parallelStream().map(value -> {
                    assertSame(caller, currentThread(), "expected values must be read on the caller thread");
                    reads[0]++;
                    return value;
                }).spliterator();
            }
        };
        var condition = factory.apply(expected);
        assertEquals(elements.size(), reads[0]);
        assertEquals(elements, condition.references().getFirst().value());
    }

    @Test
    void candidateSnapshotsRetainNullsAndArraysAsWholeValues() {
        var values = new ArrayList<>(asList(null, "ready"));
        var expected = once(values);
        var allowed = in(expected);
        values.clear();
        assertEquals(5, waiting(() -> "ready").until(allowed).length());
        String missing = null;
        assertNull(waiting(() -> missing).until(allowed));
        verify(expected).iterator();

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
        assertSame(entries, waiting(() -> entries).until(containsAllKeys(empty)));

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
        var expected = once(mutable);
        var condition = factory.apply(expected);
        var waiting = waiting(() -> actual);
        var before = diagnostic(waiting).until(condition);
        mutable.clear();
        mutable.add("changed");
        var after = diagnostic(waiting).until(condition);

        verify(expected).iterator();
        assertEquals(before.getClass(), after.getClass());
        switch (before) {
            case Satisfied(var ignoredHistory, var observed) -> {
                ACTUAL value = waiting.until(condition);
                assertSame(actual, value);
                assertSame(actual, observed);
            }
            case Failed(var ignoredHistory, var original) -> {
                var current = failure(after);
                assertEquals(original.message(), current.message());
                assertThat(current.message()).doesNotContain("changed");
                assertThrows(AwaitAssertionError.class, () -> waiting.until(condition));
            }
        }
        verify(expected).iterator();
    }

    private static <OBSERVED> ValueAwait<OBSERVED> waiting(Callable<OBSERVED> source) {
        var time = new FakeTime(0);
        return await(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(1));
    }

    private static <ELEMENT> Iterable<ELEMENT> once(List<ELEMENT> values) {
        Iterable<ELEMENT> expected = mock(CALLS_REAL_METHODS);
        when(expected.iterator()).thenAnswer(ignored -> values.iterator())
                .thenAnswer(ignored -> { throw new AssertionError("expected values were traversed again"); });
        return expected;
    }

    private static Stream<Named<Function<Iterable<String>, PreservingCondition<? super List<String>>>>> collectionFactories() {
        return Stream.of(
                named("containsAll", CollectionConditions::containsAll), named("doesNotContainAny", CollectionConditions::doesNotContainAny),
                named("doesNotContainAll", CollectionConditions::doesNotContainAll), named("containsAny", CollectionConditions::containsAny),
                named("containsExactly", CollectionConditions::containsExactly), named("doesNotContainExactly", CollectionConditions::doesNotContainExactly),
                named("containsExactlyInAnyOrder", CollectionConditions::containsExactlyInAnyOrder),
                named("doesNotContainExactlyInAnyOrder", CollectionConditions::doesNotContainExactlyInAnyOrder),
                named("containsOnly", CollectionConditions::containsOnly), named("subsetOf", CollectionConditions::subsetOf),
                named("startsWith", CollectionConditions::startsWith), named("endsWith", CollectionConditions::endsWith),
                named("containsSequence", CollectionConditions::containsSequence), named("doesNotContainSequence", CollectionConditions::doesNotContainSequence),
                named("containsSubsequence", CollectionConditions::containsSubsequence),
                named("doesNotContainSubsequence", CollectionConditions::doesNotContainSubsequence));
    }

    private static Stream<Named<Function<Iterable<String>, PreservingCondition<? super Map<String, String>>>>> mapFactories() {
        return Stream.of(
                named("containsAllKeys", MapConditions::containsAllKeys), named("doesNotContainAnyKeys", MapConditions::doesNotContainAnyKeys),
                named("containsOnlyKeys", MapConditions::containsOnlyKeys), named("containsAllValues", MapConditions::containsAllValues),
                named("doesNotContainAnyValues", MapConditions::doesNotContainAnyValues));
    }

    private static Stream<Arguments> textFactories() {
        Function<Iterable<String>, PreservingCondition<? super StringBuilder>> contains = StringConditions::containsAll;
        Function<Iterable<String>, PreservingCondition<? super StringBuilder>> excludes = StringConditions::doesNotContainAny;
        return Stream.of(argumentSet("contains present", contains, List.of("a", "b")),
                argumentSet("contains absent", contains, List.of("x", "y")),
                argumentSet("excludes present", excludes, List.of("a", "b")),
                argumentSet("excludes absent", excludes, List.of("x", "y")));
    }

    private static Stream<Named<Function<Iterable<String>, Condition>>> splittableFactories() {
        return Stream.of(named("collection containsExactly", CollectionConditions::containsExactly),
                named("map containsAllKeys", MapConditions::containsAllKeys), named("in", Conditions::in),
                named("text containsAll", StringConditions::containsAll), named("text doesNotContainAny", StringConditions::doesNotContainAny));
    }
}
