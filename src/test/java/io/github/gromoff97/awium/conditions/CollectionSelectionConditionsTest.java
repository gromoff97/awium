package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.FakeTime;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.condition.ConditionTestRuntime.evaluate;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.Status.UNSATISFIED;
import static io.github.gromoff97.awium.conditions.CollectionConditions.first;
import static io.github.gromoff97.awium.conditions.CollectionConditions.last;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionSelectionConditionsTest {

    @Test
    void singleFieldAndOverloadsReturnTypedElements() {
        var users = new ArrayList<>(List.of(new User(1), new User(2)));
        CollectionSource<ArrayList<User>> source = () -> users;

        User matching = await(source).until(CollectionConditions.single(user -> user.id() == 2));
        String typed = await((CollectionSource<List<Object>>) () -> List.of("text", 42)).until(CollectionConditions.singleElementOfType(String.class));
        String nullable = await((CollectionSource<List<String>>) () -> Arrays.asList((String) null)).until(CollectionConditions.single);

        assertSame(users.get(1), matching);
        assertEquals("text", typed);
        assertNull(nullable);
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var actual = new ArrayList<>(List.of(2, 4, 6));
        CollectionSource<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, await(source).until(CollectionConditions.all(value -> value % 2 == 0)));
        assertSame(actual, await(source).until(CollectionConditions.any(value -> value == 4)));
        assertSame(actual, await(source).until(CollectionConditions.none(value -> value < 0)));
    }

    @Test
    void quantifiersCoverTheirUnsatisfiedBranches() throws Exception {
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.all(
                (Integer value) -> value % 2 == 0), List.of(2, 3)).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.any(
                (Integer value) -> value == 4), List.of(1, 3)).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.none(
                (Integer value) -> value < 0), List.of(1, -1)).status());
    }

    @Test
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() throws Exception {
        var values = new ArrayList<>(Arrays.asList("a", "a", null));

        assertEquals(SATISFIED, evaluate(CollectionConditions.containsNull, values).status());
        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.doesNotContainNull, values).status());
        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.hasNoDuplicates, values).status());
        assertEquals(SATISFIED,
                evaluate(CollectionConditions.containsOnly("a", null), values).status());
        assertEquals(SATISFIED, evaluate(CollectionConditions.subsetOf(
                Arrays.asList("a", "b", null)), values).status());
        assertEquals(SATISFIED,
                evaluate(CollectionConditions.sizeBetween(2, 4), values).status());
        assertEquals(SATISFIED, evaluate(
                CollectionConditions.sameSizeAs(List.of(1, 2, 3)), values).status());
        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.containsOnlyNulls, List.of()).status());
        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.containsNull, List.of("a")).status());
        assertEquals(SATISFIED,
                evaluate(CollectionConditions.doesNotContainNull, List.of("a")).status());
        assertEquals(SATISFIED, evaluate(CollectionConditions.containsOnlyNulls,
                Arrays.asList(null, null)).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.containsOnlyNulls,
                Arrays.asList(null, "a")).status());
        assertEquals(SATISFIED, evaluate(CollectionConditions.hasNoDuplicates,
                List.of("a", "b")).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.containsOnly("a", null),
                Arrays.asList("a", "b", null)).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.subsetOf(
                List.of("a", "b")), List.of("a", "c")).status());
    }

    @Test
    void sameSizeAsReadsTheExpectedCollectionWhenEvaluated() throws Exception {
        var expected = new ArrayList<>(List.of(1));
        var condition = CollectionConditions.sameSizeAs(expected);
        expected.add(2);

        assertEquals(UNSATISFIED, evaluate(condition, List.of(1)).status());
        assertEquals(SATISFIED, evaluate(condition, List.of(1, 2)).status());
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() throws Exception {
        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        CollectionSource<ArrayList<Integer>> source = () -> values;

        assertEquals(1, await(source).until(first));
        assertEquals(5, await(source).until(last.because("latest business result")));
        assertEquals(3, await(source).until(CollectionConditions.element(2)));
        assertEquals(3, await(source).until(CollectionConditions.first(value -> value > 2)));
        assertEquals(3, await(source).until(CollectionConditions.last(value -> value < 5)));
        assertSame(values, await(source).until(CollectionConditions.startsWith(1, 2)));
        assertSame(values, await(source).until(CollectionConditions.endsWith(3, 5)));
        assertSame(values, await(source).until(CollectionConditions.containsSequence(2, 3)));
        assertEquals(SATISFIED, evaluate(CollectionConditions.containsSequence(1, 2),
                List.of(1, 2)).status());
        assertSame(values, await(source).until(CollectionConditions.containsSubsequence(1, 3, 5)));
        assertSame(values, await(source).until(CollectionConditions.sorted()));

        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.startsWith(1, 3), values).status());
        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.endsWith(2, 5), values).status());
        assertEquals(UNSATISFIED,
                evaluate(CollectionConditions.containsSequence(1, 3), values).status());
        assertEquals(SATISFIED, evaluate(
                CollectionConditions.doesNotContainSequence(1, 3), values).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.doesNotContainSequence(2, 3), values).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.containsSubsequence(1, 4), values).status());
        assertEquals(SATISFIED, evaluate(
                CollectionConditions.doesNotContainSubsequence(1, 4), values).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.doesNotContainSubsequence(1, 3, 5), values).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.<Integer>sorted(),
                List.of(1, 3, 2)).status());
        assertEquals(SATISFIED, evaluate(CollectionConditions.<Integer>sorted(),
                List.of(1, 1)).status());
        assertEquals(SATISFIED, evaluate(CollectionConditions.<Integer>sorted(
                java.util.Comparator.reverseOrder()), List.of(3, 2, 1)).status());

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).until(first));
        assertSame(sequencedSet, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).until(
                CollectionConditions.startsWith(1, 2)));
    }

    @Test
    void singlePredicateRequiresExactlyOneMatch() throws Exception {
        ConditionEvaluation<?> none = evaluate(
                CollectionConditions.<Integer>single(value -> value > 10), List.of(1, 2));
        ConditionEvaluation<?> many = evaluate(
                CollectionConditions.<Integer>single(value -> value > 0), List.of(1, 2));

        assertEquals(UNSATISFIED, none.status());
        assertEquals(UNSATISFIED, many.status());
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() throws Exception {
        assertEquals(UNSATISFIED, evaluate(first, List.of()).status());
        assertEquals(UNSATISFIED, evaluate(last, null).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.<String>first(
                value -> value.startsWith("r")), List.of("failed")).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.<String>last(
                value -> value.startsWith("r")), List.of("failed")).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.<String>element(2), List.of("only")).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.<String>element(1), List.of("only")).status());
        assertEquals(UNSATISFIED, evaluate(CollectionConditions.<String>element(
                0, value -> value.startsWith("r")), List.of("failed")).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.singleElementOfType(String.class),
                List.of(1, 2)).status());
        assertEquals(UNSATISFIED, evaluate(
                CollectionConditions.singleElementOfType(String.class),
                List.of("first", "second")).status());
        assertEquals("index must be non-negative", assertThrows(
                IllegalArgumentException.class,
                () -> CollectionConditions.element(-1)).getMessage());
        assertThrows(NullPointerException.class, () -> CollectionConditions.sorted(null));
    }

    @Test
    void lastReturnsTheFinalPersistenceObservation() {
        var observations = new ArrayDeque<>(List.of(List.of(1), List.of(2), List.of(3)));
        CollectionSource<List<Integer>> source = observations::removeFirst;
        FakeTime time = new FakeTime(0);

        assertEquals(3, timedCollectionAwait(source,
                new io.github.gromoff97.awium.internal.engine.WaitConfiguration(1, 5, 2), time, time).until(last));
    }

    @Test
    void explainedFirstRetainsSelectedTimeoutDiagnostics() {
        FakeTime time = new FakeTime(0);
        CollectionSource<List<String>> source = List::of;

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait(source,
                        new io.github.gromoff97.awium.internal.engine.WaitConfiguration(1, 3, 0),
                        time, time).until(first.because("a first result is required")));

        assertTrue(failure.getMessage().contains("Condition: collection has a first element"));
        assertTrue(failure.getMessage().contains("Importance: a first result is required"));
        assertTrue(failure.getMessage().contains("Mismatch: collection was empty"));
    }

    private record User(int id) {
    }
}
