package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SequencedCollection;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static io.github.gromoff97.awium.CollectionConditions.awaitCollection;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.CollectionConditions.first;
import static io.github.gromoff97.awium.CollectionConditions.last;
import static io.github.gromoff97.awium.CollectionConditions.single;
import static io.github.gromoff97.awium.CollectionConditions.subsetOf;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionSelectionConditionsTest {

    @Test
    void singleFieldAndOverloadsReturnTypedElements() {
        var pollingTime = new FakeTime(0);
        var users = new ArrayList<>(List.of(new User(1), new User(2)));
        java.util.concurrent.Callable<ArrayList<User>> source = () -> users;

        User matching = awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(matches(user -> user.id() == 2)));
        String typed = awaitCollection((java.util.concurrent.Callable<List<Object>>) () -> List.of("text", 42)).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(instanceOf(String.class)));
        String nullable = awaitCollection(() -> Arrays.asList((String) null)).usingTime(pollingTime, pollingTime).until(CollectionConditions.single);

        assertSame(users.get(1), matching);
        assertEquals("text", typed);
        assertNull(nullable);
        assertNull(awaitCollection(() -> Arrays.asList(null, "other")).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(matches(value -> value == null))));
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var pollingTime = new FakeTime(0);
        var actual = new ArrayList<>(List.of(2, 4, 6));
        java.util.concurrent.Callable<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.all(value -> value % 2 == 0)));
        assertSame(actual, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.any(value -> value == 4)));
        assertSame(actual, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.none(value -> value < 0)));
    }

    @Test
    void quantifiersCoverTheirUnsatisfiedBranches() {
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.all(
                (Integer value) -> value % 2 == 0), List.of(2, 3)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.any(
                (Integer value) -> value == 4), List.of(1, 3)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.none(
                (Integer value) -> value < 0), List.of(1, -1)).getClass());
    }

    @Test
    void subsetPreservesTypedCollectionsAcrossTerminalsAndComposition() {
        var actual = new ArrayList<>(List.of("ready"));
        var time = new FakeTime(0);
        var plain = awaitCollection(() -> actual).until(subsetOf(List.of("ready", "done")));
        var explained = awaitCollection(() -> actual).until(subsetOf(List.of("ready", "done")).because("allowed states"));
        var selected = awaitCollection(() -> List.of(actual)).until(single(subsetOf(List.of("ready")).because("allowed states")));
        var captured = awaitCollection(() -> actual).usingTime(time, time)
                .until(subsetOf(List.of("ready")), subsetOf(List.of("ready", "done")).because("allowed states"));

        assertSame(actual, plain);
        assertSame(actual, explained);
        assertSame(actual, selected);
        assertSame(actual, captured.getLast());
        assertEquals(List.of(5, 5, 5, 5), List.of(plain.getFirst().length(), explained.getFirst().length(),
                selected.getFirst().length(), captured.getLast().getFirst().length()));

        var diagnostic = awaitCollection(() -> actual).tryUntil(subsetOf(List.of("ready")).because("allowed states"));
        if (diagnostic instanceof AwaitResult.Satisfied(var attempts, var value)) {
            assertSame(actual, value);
            assertEquals(5, value.getFirst().length());
        } else {
            throw new AssertionError(diagnostic);
        }

        List<CharSequence> text = List.of("ready");
        assertSame(text, awaitCollection(() -> text).until(subsetOf(List.of("ready")).because("compatible subtype")));
    }

    @Test
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() {
        var values = new ArrayList<>(Arrays.asList("a", "a", null));

        assertEquals(Satisfied.class, evaluate(CollectionConditions.containsNull, values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.doesNotContainNull, values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.hasNoDuplicates, values).getClass());
        assertEquals(Satisfied.class,
                evaluate(CollectionConditions.containsOnly(asList("a", null)), values).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.subsetOf(
                Arrays.asList("a", "b", null)), values).getClass());
        assertEquals(Satisfied.class,
                evaluate(CollectionConditions.sizeBetween(2, 4), values).getClass());
        assertEquals(Satisfied.class, evaluate(
                CollectionConditions.size(3), values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.containsOnlyNulls, List.of()).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.containsNull, List.of("a")).getClass());
        assertEquals(Satisfied.class,
                evaluate(CollectionConditions.doesNotContainNull, List.of("a")).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.containsOnlyNulls,
                Arrays.asList(null, null)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.containsOnlyNulls,
                Arrays.asList(null, "a")).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.hasNoDuplicates,
                List.of("a", "b")).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.containsOnly(asList("a", null)),
                Arrays.asList("a", "b", null)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.subsetOf(
                List.of("a", "b")), List.of("a", "c")).getClass());
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() {
        var pollingTime = new FakeTime(0);
        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        java.util.concurrent.Callable<ArrayList<Integer>> source = () -> values;

        assertEquals(1, awaitCollection(source).usingTime(pollingTime, pollingTime).until(first));
        assertEquals(5, awaitCollection(source).usingTime(pollingTime, pollingTime).until(last.because("latest business result")));
        assertEquals(3, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.element(2)));
        assertEquals(3, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.first(value -> value > 2)));
        assertEquals(3, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.last(value -> value < 5)));
        assertSame(values, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.startsWith(List.of(1, 2))));
        assertSame(values, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.endsWith(List.of(3, 5))));
        var pair = List.of(1, 2);
        var exact = awaitCollection(() -> pair).usingTime(pollingTime, pollingTime).upTo(java.time.Duration.ofNanos(1));
        assertSame(pair, exact.until(CollectionConditions.startsWith(List.of(1, 2))));
        assertSame(pair, exact.until(CollectionConditions.endsWith(List.of(1, 2))));
        assertSame(values, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.containsSequence(List.of(2, 3))));
        assertEquals(Satisfied.class, evaluate(CollectionConditions.containsSequence(List.of(1, 2)),
                List.of(1, 2)).getClass());
        assertSame(values, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.containsSubsequence(List.of(1, 3, 5))));
        assertSame(values, awaitCollection(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.sorted(Integer::compareTo)));

        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.startsWith(List.of(1, 3)), values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.endsWith(List.of(2, 5)), values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.containsSequence(List.of(1, 3)), values).getClass());
        assertEquals(Satisfied.class, evaluate(
                CollectionConditions.doesNotContainSequence(List.of(1, 3)), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.doesNotContainSequence(List.of(2, 3)), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.containsSubsequence(List.of(1, 4)), values).getClass());
        assertEquals(Satisfied.class, evaluate(
                CollectionConditions.doesNotContainSubsequence(List.of(1, 4)), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.doesNotContainSubsequence(List.of(1, 3, 5)), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.sorted(Integer::compareTo),
                List.of(1, 3, 2)).getClass());
        var equalElements = List.of(1, 1);
        assertSame(equalElements, awaitCollection(() -> equalElements).usingTime(pollingTime, pollingTime)
                .until(CollectionConditions.sorted(Integer::compareTo).because("equal elements retain their order")));
        assertEquals(Satisfied.class, evaluate(CollectionConditions.sorted(
                java.util.Comparator.comparingInt(Integer::intValue).reversed()), List.of(3, 2, 1)).getClass());

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, awaitCollection(() -> sequencedSet).usingTime(pollingTime, pollingTime).until(first));
        assertSame(sequencedSet, awaitCollection(() -> sequencedSet).usingTime(pollingTime, pollingTime).until(
                CollectionConditions.startsWith(List.of(1, 2))));
    }

    @Test
    void singleMatchingConditionRequiresExactlyOneMatch() {
        var time = new FakeTime(0);
        var waiting = awaitCollection(() -> List.of(1, 2)).usingTime(time, time).upTo(ofNanos(1));

        var none = assertFailure(TIMEOUT, () -> waiting.until(single(matches(value -> value > 10))));
        var many = assertFailure(TIMEOUT, () -> waiting.until(single(matches(value -> value > 0))));

        assertTrue(none.getMessage().contains("value did not match"));
        assertTrue(many.getMessage().contains("more than one value matched"));
    }

    @Test
    void orderedConditionsRejectIncompletePrefixesAndSuffixes() {
        for (List<Integer> actual : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            assertEquals(Unsatisfied.class, evaluate(CollectionConditions.startsWith(List.of(1, 2, 3)), actual).getClass());
            assertEquals(Unsatisfied.class, evaluate(CollectionConditions.endsWith(List.of(1, 2, 3)), actual).getClass());
            assertEquals(Unsatisfied.class, evaluate(CollectionConditions.containsSequence(List.of(1, 2, 3)), actual).getClass());
            assertSame(actual, ((Satisfied<?>) evaluate(CollectionConditions.doesNotContainSequence(List.of(1, 2, 3)), actual)).result());
        }
        var empty = new ArrayDeque<Integer>();
        assertSame(empty, ((Satisfied<?>) evaluate(CollectionConditions.sorted(Integer::compareTo), empty)).result());
    }

    @Test
    void orderedChecksPreserveSequentialAndSequencedContainersWithNullsAndArrays() {
        var elements = Arrays.asList(null, new int[] {1, 2}, "gap", new int[] {3, 4});
        List<SequencedCollection<Object>> containers = List.of(
                new LinkedList<>(elements), new LinkedHashSet<>(elements));
        for (var actual : containers) {
            var time = new FakeTime(0);
            var waiting = awaitCollection(() -> actual).usingTime(time, time);

            assertSame(actual, waiting.until(CollectionConditions.startsWith(asList(null, new int[] {1, 2})).because("prefix")));
            assertSame(actual, waiting.until(CollectionConditions.endsWith(List.of("gap", new int[] {3, 4})).because("suffix")));
            assertSame(actual, waiting.until(CollectionConditions.containsSequence(List.of(new int[] {1, 2}, "gap"))));
            var captured = waiting.tryUntil(CollectionConditions.containsSubsequence(asList(null, new int[] {3, 4})));
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, captured).result());
        }
    }

    @Test
    void orderedExpectationsTreatReferenceArraysAsSingleElementsWithoutTypeArguments() {
        var expected = singletonList(new String[] {"a", "b"});
        var unrelated = singletonList(new String[] {"other"});
        var actual = singletonList(new String[] {"a", "b"});
        var time = new FakeTime(0);
        var waiting = awaitCollection(() -> actual).usingTime(time, time);

        for (var check : List.of(CollectionConditions.startsWith(expected), CollectionConditions.endsWith(expected),
                CollectionConditions.containsSequence(expected), CollectionConditions.containsSubsequence(expected),
                CollectionConditions.doesNotContainSequence(unrelated), CollectionConditions.doesNotContainSubsequence(unrelated))) {
            var selected = waiting.until(check.because("an array is one element"));
            AwaitResult<List<String[]>, List<String[]>> attempt = waiting.tryUntil(check);

            assertEquals("a", selected.getFirst()[0]);
            assertSame(actual, selected);
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, attempt).result());
        }
    }

    @Test
    void impossiblePatternLengthDoesNotReenterContainerCallbacks() {
        for (var elements : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            for (boolean negative : List.of(false, true)) {
                var conditions = negative
                        ? List.of(CollectionConditions.doesNotContainSequence(List.of(1, 2, 3)))
                        : List.of(CollectionConditions.startsWith(List.of(1, 2, 3)), CollectionConditions.endsWith(List.of(1, 2, 3)),
                                CollectionConditions.containsSequence(List.of(1, 2, 3)));
                for (var condition : conditions) {
                    var actual = new AbstractList<Integer>() {
                        private int sizeReads;

                        @Override
                        public int size() {
                            if (++sizeReads > 1) throw new AssertionError("an impossible pattern must not reenter size");
                            return elements.size();
                        }

                        @Override
                        public Integer get(int index) {
                            throw new AssertionError("an impossible pattern must not retrieve elements");
                        }
                    };
                    var evaluation = evaluate(condition, actual);
                    assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                    if (negative) assertSame(actual, ((Satisfied<?>) evaluation).result());
                }
            }
        }
    }

    @Test
    void nestedSelectorsWaitForANullCollectionToBecomeAvailable() {
        var observations = Arrays.asList(null, List.of(" "), null, List.of(" ")).iterator();
        var time = new FakeTime(0);
        var waiting = awaitCollection(observations::next).usingTime(time, time)
                .every(java.time.Duration.ofNanos(1)).upTo(java.time.Duration.ofNanos(3));

        String last = waiting.until(CollectionConditions.last(String::isBlank).because("latest blank value"));
        String single = waiting.until(CollectionConditions.single(matches(String::isBlank)).because("only blank value"));

        assertEquals(" ", last);
        assertEquals(" ", single);
        assertFalse(observations.hasNext());
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() {
        var time = new FakeTime(0);
        var missing = awaitCollection(() -> (List<String>) null).usingTime(time, time).upTo(ofNanos(1));
        var values = awaitCollection(() -> List.of("failed")).usingTime(time, time).upTo(ofNanos(1));

        assertFailure(TIMEOUT, () -> awaitCollection(() -> List.of())
                .usingTime(time, time).upTo(ofNanos(1)).until(first));
        assertFailure(TIMEOUT, () -> missing.until(last));
        assertFailure(TIMEOUT, () -> missing.until(CollectionConditions.first(value -> true)));
        assertFailure(TIMEOUT, () -> missing.until(CollectionConditions.element(0)));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.first(value -> value.startsWith("r"))));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.last(value -> value.startsWith("r"))));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.element(2)));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.element(1)));
        assertFailure(TIMEOUT, () -> values.until(CollectionConditions.element(0, value -> value.startsWith("r"))));
        assertFailure(TIMEOUT, () -> awaitCollection(() -> List.of(1, 2))
                .usingTime(time, time).upTo(ofNanos(1)).until(single(instanceOf(String.class))));
        assertFailure(TIMEOUT, () -> awaitCollection(() -> List.of("first", "second"))
                .usingTime(time, time).upTo(ofNanos(1)).until(single(instanceOf(String.class))));
        assertEquals("index must be non-negative", assertThrows(
                IllegalArgumentException.class,
                () -> CollectionConditions.element(-1)).getMessage());
        assertThrows(NullPointerException.class, () -> CollectionConditions.sorted(null));
    }

    @Test
    void lastReturnsTheFinalPersistenceObservation() {
        var observations = new ArrayDeque<>(List.of(List.of(1), List.of(2), List.of(3)));
        java.util.concurrent.Callable<List<Integer>> source = observations::removeFirst;
        FakeTime time = new FakeTime(0);

        assertEquals(3, awaitCollection(source).usingTime(time, time).every(ofNanos(1))
                .upTo(ofNanos(5)).persisting(ofNanos(2)).until(last));
    }

    @Test
    void explainedFirstRetainsSelectedTimeoutDiagnostics() {
        FakeTime time = new FakeTime(0);
        java.util.concurrent.Callable<List<String>> source = List::of;

        var failure = assertFailure(TIMEOUT,
                () -> awaitCollection(source).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3))
                        .until(first.because("a first result is required")));

        assertTrue(failure.getMessage().contains("Condition: collection has a first element"));
        assertTrue(failure.getMessage().contains("Because: a first result is required"));
        assertTrue(failure.getMessage().contains("Mismatch: collection was empty"));
    }

    private record User(int id) {
    }
}
