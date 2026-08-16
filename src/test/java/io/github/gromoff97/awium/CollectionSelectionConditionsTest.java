package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.CollectionCondition;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.SATISFIED;
import static io.github.gromoff97.awium.conditioning.Evaluation.Status.UNSATISFIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CollectionSelectionConditionsTest {

    @Test
    void singleFieldAndOverloadsReturnTypedElements() {
        var users = new ArrayList<>(List.of(new User(1), new User(2)));
        CollectionSource<ArrayList<User>> source = () -> users;

        User matching = await(source).until(CollectionCondition.singleElement(user -> user.id() == 2));
        String typed = await((CollectionSource<List<Object>>) () -> List.of("text", 42)).until(CollectionCondition.singleElement(String.class));
        String nullable = await((CollectionSource<List<String>>) () -> Arrays.asList((String) null)).until(CollectionCondition.singleElement);

        assertSame(users.get(1), matching);
        assertEquals("text", typed);
        assertNull(nullable);
    }

    @Test
    void quantifiersPreserveTheConcreteCollection() {
        var actual = new ArrayList<>(List.of(2, 4, 6));
        CollectionSource<ArrayList<Integer>> source = () -> actual;

        assertSame(actual, await(source).until(CollectionCondition.allElements(value -> value % 2 == 0)));
        assertSame(actual, await(source).until(CollectionCondition.anyElement(value -> value == 4)));
        assertSame(actual, await(source).until(CollectionCondition.noElement(value -> value < 0)));
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
        assertEquals(SATISFIED, CollectionCondition.elementCountBetween(2, 4).delegate().evaluate(values).status());
        assertEquals(SATISFIED, CollectionCondition.sameElementCountAs(List.of(1, 2, 3))
                .delegate().evaluate(values).status());
        assertEquals(UNSATISFIED, CollectionCondition.containsOnlyNulls.delegate().evaluate(List.of()).status());
    }

    @Test
    void orderedConditionsCoverPositionsSequencesAndSorting() throws Exception {
        var values = new ArrayList<>(List.of(1, 2, 3, 5));
        CollectionSource<ArrayList<Integer>> source = () -> values;

        assertEquals(1, await(source).until(CollectionCondition.first()));
        assertEquals(5, await(source).until(CollectionCondition.last()));
        assertEquals(3, await(source).until(CollectionCondition.element(2)));
        assertEquals(3, await(source).until(CollectionCondition.first(value -> value > 2)));
        assertEquals(3, await(source).until(CollectionCondition.last(value -> value < 5)));
        assertSame(values, await(source).until(CollectionCondition.startsWithElements(1, 2)));
        assertSame(values, await(source).until(CollectionCondition.endsWithElements(3, 5)));
        assertSame(values, await(source).until(CollectionCondition.containsSequence(2, 3)));
        assertSame(values, await(source).until(CollectionCondition.containsSubsequence(1, 3, 5)));
        assertSame(values, await(source).until(CollectionCondition.sorted()));

        var sequencedSet = new LinkedHashSet<>(List.of(1, 2, 3));
        assertEquals(1, await((CollectionSource<LinkedHashSet<Integer>>) () -> sequencedSet).until(CollectionCondition.first()));
    }

    @Test
    void singlePredicateRequiresExactlyOneMatch() throws Exception {
        Evaluation<?> none = CollectionCondition.<Integer>singleElement(value -> value > 10)
                .evaluate(List.of(1, 2));
        Evaluation<?> many = CollectionCondition.<Integer>singleElement(value -> value > 0)
                .evaluate(List.of(1, 2));

        assertEquals(UNSATISFIED, none.status());
        assertEquals(UNSATISFIED, many.status());
    }

    private record User(int id) {
    }
}
