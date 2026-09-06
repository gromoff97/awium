package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.condition.ConditionEvaluation.Satisfied;
import io.github.gromoff97.awium.condition.ConditionEvaluation.Unsatisfied;
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
import static io.github.gromoff97.awium.conditions.CollectionConditions.first;
import static io.github.gromoff97.awium.conditions.CollectionConditions.last;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
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

        User matching = await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(user -> user.id() == 2));
        String typed = await((CollectionSource<List<Object>>) () -> List.of("text", 42)).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(instanceOf(String.class)));
        String nullable = await((CollectionSource<List<String>>) () -> Arrays.asList((String) null)).usingTime(pollingTime, pollingTime).until(CollectionConditions.single);

        assertSame(users.get(1), matching);
        assertEquals("text", typed);
        assertNull(nullable);
        assertNull(await(() -> Arrays.asList(null, "other")).usingTime(pollingTime, pollingTime).until(CollectionConditions.single(value -> value == null)));
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
    void quantifiersCoverTheirUnsatisfiedBranches() throws Exception {
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.all(
                (Integer value) -> value % 2 == 0), List.of(2, 3)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.any(
                (Integer value) -> value == 4), List.of(1, 3)).getClass());
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.none(
                (Integer value) -> value < 0), List.of(1, -1)).getClass());
    }

    @Test
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() throws Exception {
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
    void sameSizeAsReadsTheExpectedCollectionWhenEvaluated() throws Exception {
        var expected = new ArrayList<>(List.of(1));
        var condition = CollectionConditions.sameSizeAs(expected);
        expected.add(2);

        assertEquals(Unsatisfied.class, evaluate(condition, List.of(1)).getClass());
        assertEquals(Satisfied.class, evaluate(condition, List.of(1, 2)).getClass());
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() throws Exception {
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
        assertSame(values, await(source).usingTime(pollingTime, pollingTime).until(CollectionConditions.sorted()));

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
        assertEquals(Unsatisfied.class, evaluate(CollectionConditions.<Integer>sorted(),
                List.of(1, 3, 2)).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.<Integer>sorted(),
                List.of(1, 1)).getClass());
        assertEquals(Satisfied.class, evaluate(CollectionConditions.<Integer>sorted(
                java.util.Comparator.reverseOrder()), List.of(3, 2, 1)).getClass());

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).usingTime(pollingTime, pollingTime).until(first));
        assertSame(sequencedSet, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).usingTime(pollingTime, pollingTime).until(
                CollectionConditions.startsWith(1, 2)));
    }

    @Test
    void singlePredicateRequiresExactlyOneMatch() throws Exception {
        ConditionEvaluation<?> none = evaluate(
                CollectionConditions.<Integer>single(value -> value > 10), List.of(1, 2));
        ConditionEvaluation<?> many = evaluate(
                CollectionConditions.<Integer>single(value -> value > 0), List.of(1, 2));

        assertEquals(Unsatisfied.class, none.getClass());
        assertEquals(Unsatisfied.class, many.getClass());
        assertEquals("no collection element matched", ((Unsatisfied<?>) none).mismatch());
        assertEquals("more than one collection element matched", ((Unsatisfied<?>) many).mismatch());
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() throws Exception {
        assertEquals(Unsatisfied.class, evaluate(first, List.of()).getClass());
        assertEquals(Unsatisfied.class, evaluate(last, null).getClass());
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
