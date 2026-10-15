package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Satisfied;
import io.github.gromoff97.awium.ConditionResult.Unsatisfied;
import io.github.gromoff97.awium.Source.CollectionSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static io.github.gromoff97.awium.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.CollectionConditions.first;
import static io.github.gromoff97.awium.CollectionConditions.last;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionSelectionConditionsTest {

    @Test
    void singleFieldAndOverloadsReturnTypedElements() {
        var pollingTime = new FakeTime(0);
        var users = new ArrayList<>(List.of(new User(1), new User(2)));
        CollectionSource<ArrayList<User>> source = () -> users;

        User matching = await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(matches(user -> user.id() == 2)));
        String typed = await((CollectionSource<List<Object>>) () -> List.of("text", 42)).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(instanceOf(String.class)));
        String nullable = await((CollectionSource<List<String>>) () -> Arrays.asList((String) null)).usingTime(pollingTime, pollingTime).until(CollectionConditions.single);

        assertSame(users.get(1), matching);
        assertEquals("text", typed);
        assertNull(nullable);
        assertNull(await(() -> Arrays.asList(null, "other")).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(matches(value -> value == null))));
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var pollingTime = new FakeTime(0);
        var actual = new ArrayList<>(List.of(2, 4, 6));
        CollectionSource<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.all(value -> value % 2 == 0)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.any(value -> value == 4)));
        assertSame(actual, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.none(value -> value < 0)));
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
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() {
        var values = new ArrayList<>(Arrays.asList("a", "a", null));

        assertEquals(Satisfied.class, evaluate(CollectionConditions.containsNull, values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.doesNotContainNull, values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.hasNoDuplicates, values).getClass());
        assertEquals(Satisfied.class,
                evaluate(CollectionConditions.containsOnly("a", null), values).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.subsetOf(
                Arrays.asList("a", "b", null)), values).getClass());
        assertEquals(Satisfied.class,
                evaluate(CollectionConditions.sizeBetween(2, 4), values).getClass());
        assertEquals(Satisfied.class, evaluate(
                CollectionConditions.sameSizeAs(List.of(1, 2, 3)), values).getClass());
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
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.containsOnly("a", null),
                Arrays.asList("a", "b", null)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.subsetOf(
                List.of("a", "b")), List.of("a", "c")).getClass());
    }

    @Test
    void sameSizeAsReadsTheCurrentExpectedSizeOnEachEvaluation() {
        var expected = new ArrayList<>(List.of(1));
        var condition = CollectionConditions.sameSizeAs(expected);
        assertEquals(Satisfied.class, evaluate(condition, List.of(1)).getClass());
        expected.add(2);

        assertEquals(Unsatisfied.class, evaluate(condition, List.of(1)).getClass());
        assertEquals(Satisfied.class, evaluate(condition, List.of(1, 2)).getClass());
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() {
        var pollingTime = new FakeTime(0);
        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        CollectionSource<ArrayList<Integer>> source = () -> values;

        assertEquals(1, await(source).usingTime(pollingTime, pollingTime).until(first));
        assertEquals(5, await(source).usingTime(pollingTime, pollingTime).until(last.because("latest business result")));
        assertEquals(3, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.element(2)));
        assertEquals(3, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.first(value -> value > 2)));
        assertEquals(3, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.last(value -> value < 5)));
        assertSame(values, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.startsWith(1, 2)));
        assertSame(values, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.endsWith(3, 5)));
        assertSame(values, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.containsSequence(2, 3)));
        assertEquals(Satisfied.class, evaluate(CollectionConditions.containsSequence(1, 2),
                List.of(1, 2)).getClass());
        assertSame(values, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.containsSubsequence(1, 3, 5)));
        assertSame(values, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.sorted(Integer::compareTo)));

        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.startsWith(1, 3), values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.endsWith(2, 5), values).getClass());
        assertEquals(Unsatisfied.class,
                evaluate(CollectionConditions.containsSequence(1, 3), values).getClass());
        assertEquals(Satisfied.class, evaluate(
                CollectionConditions.doesNotContainSequence(1, 3), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.doesNotContainSequence(2, 3), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.containsSubsequence(1, 4), values).getClass());
        assertEquals(Satisfied.class, evaluate(
                CollectionConditions.doesNotContainSubsequence(1, 4), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.doesNotContainSubsequence(1, 3, 5), values).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.sorted(Integer::compareTo),
                List.of(1, 3, 2)).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.sorted(Integer::compareTo),
                List.of(1, 1)).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.sorted(
                java.util.Comparator.comparingInt(Integer::intValue).reversed()), List.of(3, 2, 1)).getClass());

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).usingTime(pollingTime, pollingTime).until(first));
        assertSame(sequencedSet, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).usingTime(pollingTime, pollingTime).until(
                CollectionConditions.startsWith(1, 2)));
    }

    @Test
    void singleMatchingConditionRequiresExactlyOneMatch() {
        ConditionResult<?> none = evaluate(
                CollectionConditions.<Integer>single(matches(value -> value > 10)), List.of(1, 2));
        ConditionResult<?> many = evaluate(
                CollectionConditions.<Integer>single(matches(value -> value > 0)), List.of(1, 2));

        assertEquals(Unsatisfied.class, none.getClass());
        assertEquals(Unsatisfied.class, many.getClass());
        assertEquals("value did not match", ((Unsatisfied<?>) none).mismatch());
        assertEquals("more than one value matched", ((Unsatisfied<?>) many).mismatch());
    }

    @Test
    void orderedConditionsRejectIncompletePrefixesAndSuffixes() {
        for (List<Integer> actual : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            assertEquals(Unsatisfied.class, evaluate(CollectionConditions.startsWith(1, 2, 3), actual).getClass());
            assertEquals(Unsatisfied.class, evaluate(CollectionConditions.endsWith(1, 2, 3), actual).getClass());
            assertEquals(Unsatisfied.class, evaluate(CollectionConditions.containsSequence(1, 2, 3), actual).getClass());
            assertSame(actual, ((Satisfied<?>) evaluate(CollectionConditions.doesNotContainSequence(1, 2, 3), actual)).result());
        }
        var empty = new ArrayDeque<Integer>();
        assertSame(empty, ((Satisfied<?>) evaluate(CollectionConditions.sorted(Integer::compareTo), empty)).result());
    }

    @Test
    void impossibleSequenceLengthDoesNotReenterContainerCallbacks() {
        for (var elements : List.of(List.<Integer>of(), List.of(1), List.of(1, 2))) {
            for (boolean negative : List.of(false, true)) {
                var actual = new ArrayList<>(elements) {
                    private int sizeReads;

                    @Override
                    public int size() {
                        if (++sizeReads > 1) throw new AssertionError("an impossible sequence must not reenter size");
                        return super.size();
                    }

                    @Override
                    public Integer get(int index) {
                        throw new AssertionError("an impossible sequence must not retrieve elements");
                    }
                };
                var condition = negative ? CollectionConditions.doesNotContainSequence(1, 2, 3)
                        : CollectionConditions.containsSequence(1, 2, 3);
                var evaluation = evaluate(condition, actual);
                assertEquals(negative ? Satisfied.class : Unsatisfied.class, evaluation.getClass());
                if (negative) assertSame(actual, ((Satisfied<?>) evaluation).result());
            }
        }
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() {
        assertEquals(Unsatisfied.class, evaluate(first, List.of()).getClass());
        assertEquals(Unsatisfied.class, evaluate(last, null).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.<String>first(value -> true), null).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.<String>element(0), null).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.<String>first(
                value -> value.startsWith("r")), List.of("failed")).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.<String>last(
                value -> value.startsWith("r")), List.of("failed")).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.<String>element(2), List.of("only")).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.<String>element(1), List.of("only")).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.<String>element(
                0, value -> value.startsWith("r")), List.of("failed")).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.single(instanceOf(String.class)),
                List.of(1, 2)).getClass());
        assertEquals(Unsatisfied.class, evaluate(
                CollectionConditions.single(instanceOf(String.class)),
                List.of("first", "second")).getClass());
        assertEquals("index must be non-negative", assertThrows(
                IllegalArgumentException.class,
                () -> CollectionConditions.element(-1)).getMessage());
        assertThrows(NullPointerException.class, () -> CollectionConditions.sorted(null));
    }

    @Test
    void indexedMismatchUsesTheSizeObservedByTheCheck() {
        var actual = new ArrayList<String>() {
            @Override
            public int size() {
                int observed = super.size();
                if (observed == 0) add("ready");
                return observed;
            }
        };

        var result = evaluate(CollectionConditions.<String>element(0), actual);
        assertEquals("list size was 0", ((Unsatisfied<?>) result).mismatch());
    }

    @Test
    void lastReturnsTheFinalPersistenceObservation() {
        var observations = new ArrayDeque<>(List.of(List.of(1), List.of(2), List.of(3)));
        CollectionSource<List<Integer>> source = observations::removeFirst;
        FakeTime time = new FakeTime(0);

        assertEquals(3, timedCollectionAwait(source,
                new io.github.gromoff97.awium.WaitConfiguration(1, 5, 2), time, time).until(last));
    }

    @Test
    void explainedFirstRetainsSelectedTimeoutDiagnostics() {
        FakeTime time = new FakeTime(0);
        CollectionSource<List<String>> source = List::of;

        var failure = assertFailure(TIMEOUT,
                () -> timedCollectionAwait(source,
                        new io.github.gromoff97.awium.WaitConfiguration(1, 3, 0),
                        time, time).until(first.because("a first result is required")));

        assertTrue(failure.getMessage().contains("Condition: collection has a first element"));
        assertTrue(failure.getMessage().contains("Importance: a first result is required"));
        assertTrue(failure.getMessage().contains("Mismatch: collection was empty"));
    }

    private record User(int id) {
    }
}
