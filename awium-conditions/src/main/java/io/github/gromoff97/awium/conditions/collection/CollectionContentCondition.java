package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static io.github.gromoff97.awium.conditions.collection.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.prefixMatches;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.sameElementsInAnyOrder;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

record CollectionContentCondition<INPUT extends Collection<?>>(List<?> expected, Content content, boolean negated) implements PreservingCondition<INPUT> {
    enum Content { EXACT, ANY_ORDER, ONLY, SUBSET }

    CollectionContentCondition(Iterable<?> expected, Content content, boolean negated) {
        this(snapshot(expected, negated ? "unexpected elements" : "expected elements"), content, negated);
    }

    @Override
    public String description() {
        return "collection " + switch (content) {
            case EXACT, ANY_ORDER -> (negated ? "does not contain exactly the unexpected elements" : "contains exactly the expected elements")
                    + (content == Content.ANY_ORDER ? " in any order" : "");
            case ONLY -> "contains only the expected elements";
            case SUBSET -> "is a subset of the expected elements";
        };
    }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", expected)); }

    @Override
    public ConditionResult<?> probe(INPUT actual) {
        if (actual == null) return unsatisfied("collection was null");
        boolean matched = switch (content) {
            case EXACT -> exactContent(actual, expected);
            case ANY_ORDER -> exactContentInAnyOrder(actual, expected);
            case ONLY -> sameDistinctElements(actual, expected);
            case SUBSET -> matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)));
        };
        return matched != negated ? satisfied(actual) : unsatisfied("collection " + switch (content) {
            case EXACT, ANY_ORDER -> (negated ? "contained exactly the unexpected elements" : "did not contain exactly the expected elements")
                + (content == Content.ANY_ORDER ? " in any order" : "");
            case ONLY -> "did not contain only the expected elements";
            case SUBSET -> "was not a subset of the expected elements";
        });
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Iterator<?> values = actual.iterator();
        return prefixMatches(values, expected) && !values.hasNext();
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) {
        return actual.size() == expected.size()
                && sameElementsInAnyOrder(actual.iterator(), expected);
    }
}
