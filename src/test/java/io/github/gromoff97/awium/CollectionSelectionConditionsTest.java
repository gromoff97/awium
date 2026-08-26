package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.await.AwaitTestAccess.timedCollectionAwait;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.first;
import static io.github.gromoff97.awium.conditioning.conditions.CollectionCondition.last;
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

        User matching = await(source).until(CollectionCondition.single(user -> user.id() == 2));
        String typed = await((CollectionSource<List<Object>>) () -> List.of("text", 42)).until(CollectionCondition.singleElementOfType(String.class));
        String nullable = await((CollectionSource<List<String>>) () -> Arrays.asList((String) null)).until(CollectionCondition.single);

        assertSame(users.get(1), matching);
        assertEquals("text", typed);
        assertNull(nullable);
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var actual = new ArrayList<>(List.of(2, 4, 6));
        CollectionSource<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, await(source).until(CollectionCondition.all(value -> value % 2 == 0)));
        assertSame(actual, await(source).until(CollectionCondition.any(value -> value == 4)));
        assertSame(actual, await(source).until(CollectionCondition.none(value -> value < 0)));
    }

    @Test
    void quantifiersCoverTheirUnsatisfiedBranches() throws Exception {
        assertEquals(UNSATISFIED, CollectionCondition.all((Integer value) -> value % 2 == 0)
                .delegate().evaluate(List.of(2, 3)).status());
        assertEquals(UNSATISFIED, CollectionCondition.any((Integer value) -> value == 4)
                .delegate().evaluate(List.of(1, 3)).status());
        assertEquals(UNSATISFIED, CollectionCondition.none((Integer value) -> value < 0)
                .delegate().evaluate(List.of(1, -1)).status());
    }

    @Test
    void contentConditionsCoverNullsDuplicatesSetsAndSizeRanges() throws Exception {
        var values = new ArrayList<>(Arrays.asList("a", "a", null));

        assertEquals(SATISFIED, CollectionCondition.containsNull.delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.doesNotContainNull.delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.hasNoDuplicates.delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.containsOnly("a", null).delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.subsetOf(Arrays.asList("a", "b", null))
                .delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.sizeBetween(2, 4).delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.sameSizeAs(List.of(1, 2, 3))
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsOnlyNulls.delegate().evaluate(List.of()).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsNull.delegate().evaluate(List.of("a")).status());
        assertEquals(SATISFIED, CollectionCondition.doesNotContainNull.delegate().evaluate(List.of("a")).status());
        assertEquals(SATISFIED, CollectionCondition.containsOnlyNulls.delegate()
                .evaluate(Arrays.asList(null, null)).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsOnlyNulls.delegate()
                .evaluate(Arrays.asList(null, "a")).status());
        assertEquals(SATISFIED, CollectionCondition.hasNoDuplicates.delegate().evaluate(List.of("a", "b")).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsOnly("a", null).delegate()
                .evaluate(Arrays.asList("a", "b", null)).status());
        assertEquals(UNSATISFIED, CollectionCondition.subsetOf(List.of("a", "b"))
                .delegate().evaluate(List.of("a", "c")).status());
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() throws Exception {
        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        CollectionSource<ArrayList<Integer>> source = () -> values;

        assertEquals(1, await(source).until(first));
        assertEquals(5, await(source).until(last.because("latest business result")));
        assertEquals(3, await(source).until(CollectionCondition.element(2)));
        assertEquals(3, await(source).until(CollectionCondition.first(value -> value > 2)));
        assertEquals(3, await(source).until(CollectionCondition.last(value -> value < 5)));
        assertSame(values, await(source).until(CollectionCondition.startsWith(1, 2)));
        assertSame(values, await(source).until(CollectionCondition.endsWith(3, 5)));
        assertSame(values, await(source).until(CollectionCondition.containsSequence(2, 3)));
        assertSame(values, await(source).until(CollectionCondition.containsSubsequence(1, 3, 5)));
        assertSame(values, await(source).until(CollectionCondition.sorted()));

        assertEquals(UNSATISFIED, CollectionCondition.startsWith(1, 3)
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.endsWith(2, 5)
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsSequence(1, 3)
                .delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.doesNotContainSequence(1, 3)
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.doesNotContainSequence(2, 3)
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsSubsequence(1, 4)
                .delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.doesNotContainSubsequence(1, 4)
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.doesNotContainSubsequence(1, 3, 5)
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.<Integer>sorted().delegate().evaluate(List.of(1, 3, 2)).status());
        assertEquals(SATISFIED, CollectionCondition.<Integer>sorted(java.util.Comparator.reverseOrder())
                .delegate().evaluate(List.of(3, 2, 1)).status());

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).until(first));
    }

    @Test
    void singlePredicateRequiresExactlyOneMatch() throws Exception {
        Evaluation<?> none = CollectionCondition.<Integer>single(value -> value > 10)
                .evaluate(List.of(1, 2));
        Evaluation<?> many = CollectionCondition.<Integer>single(value -> value > 0)
                .evaluate(List.of(1, 2));

        assertEquals(UNSATISFIED, none.status());
        assertEquals(UNSATISFIED, many.status());
    }

    @Test
    void selectorsCoverMissingNullAndInvalidPositions() throws Exception {
        assertEquals(UNSATISFIED, first.delegate().evaluate(List.of()).status());
        assertEquals(UNSATISFIED, last.delegate().evaluate(null).status());
        assertEquals(UNSATISFIED, CollectionCondition.<String>first(value -> value.startsWith("r"))
                .evaluate(List.of("failed")).status());
        assertEquals(UNSATISFIED, CollectionCondition.<String>last(value -> value.startsWith("r"))
                .evaluate(List.of("failed")).status());
        assertEquals(UNSATISFIED, CollectionCondition.<String>element(2).evaluate(List.of("only")).status());
        assertEquals(UNSATISFIED, CollectionCondition.<String>element(0, value -> value.startsWith("r"))
                .evaluate(List.of("failed")).status());
        assertEquals(UNSATISFIED, CollectionCondition.singleElementOfType(String.class)
                .evaluate(List.of(1, 2)).status());
        assertEquals(UNSATISFIED, CollectionCondition.singleElementOfType(String.class)
                .evaluate(List.of("first", "second")).status());
        assertThrows(IllegalArgumentException.class, () -> CollectionCondition.element(-1));
        assertThrows(NullPointerException.class, () -> CollectionCondition.sorted(null));
    }

    @Test
    void lastReturnsTheFinalStabilizationObservation() {
        var observations = new ArrayDeque<>(List.of(List.of(1), List.of(2), List.of(3)));
        CollectionSource<List<Integer>> source = observations::removeFirst;
        FakeTime time = new FakeTime(0);

        assertEquals(3, timedCollectionAwait(source,
                new io.github.gromoff97.awium.engine.WaitConfiguration(1, 5, 2), time, time).until(last));
    }

    @Test
    void explainedFirstRetainsSelectedTimeoutDiagnostics() {
        FakeTime time = new FakeTime(0);
        CollectionSource<List<String>> source = List::of;

        AwaitTimeoutException failure = assertThrows(AwaitTimeoutException.class,
                () -> timedCollectionAwait(source,
                        new io.github.gromoff97.awium.engine.WaitConfiguration(1, 3, 0),
                        time, time).until(first.because("a first result is required")));

        assertTrue(failure.getMessage().contains("Expectation: collection has a first element"));
        assertTrue(failure.getMessage().contains("Importance: a first result is required"));
        assertTrue(failure.getMessage().contains("Mismatch: collection was empty"));
    }

    private record User(int id) {
    }
}
