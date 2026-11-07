package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static io.github.gromoff97.awium.conditions.collection.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.prefixMatches;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.sameElementsInAnyOrder;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;

record CollectionContentCondition<INPUT extends Collection<?>>(List<?> expected, Content content, boolean negated) implements PreservingCondition<INPUT> {
    enum Content { EXACT, ANY_ORDER, ONLY, SUBSET }

    CollectionContentCondition(Iterable<?> expected, Content content, boolean negated) {
        this(snapshot(expected, negated), content, negated);
    }

    @Override
    public String description() {
        return "collection " + switch (content) {
            case EXACT, ANY_ORDER -> {
                String description;
                if (negated) {
                    description = "does not contain exactly the unexpected elements";
                } else {
                    description = "contains exactly the expected elements";
                }
                if (content == Content.ANY_ORDER) {
                    description += " in any order";
                }
                yield description;
            }
            case ONLY -> "contains only the expected elements";
            case SUBSET -> "is a subset of the expected elements";
        };
    }
    @Override
    public List<Reference<?>> references() {
        if (negated) {
            return List.of(new Reference<>("Unexpected", expected));
        }
        return List.of(new Reference<>("Expected", expected));
    }

    @Override
    public AttemptResult<?> probe(INPUT actual) {
        if (actual == null) return unsatisfied("collection was null");
        boolean matched = switch (content) {
            case EXACT -> exactContent(actual, expected);
            case ANY_ORDER -> exactContentInAnyOrder(actual, expected);
            case ONLY -> sameDistinctElements(actual, expected);
            case SUBSET -> matchesAll(actual, value -> matchesAny(expected, candidate -> deepEquals(value, candidate)));
        };
        if (matched != negated) {
            return satisfied(actual);
        }
        return unsatisfied("collection " + switch (content) {
            case EXACT, ANY_ORDER -> {
                String mismatch;
                if (negated) {
                    mismatch = "contained exactly the unexpected elements";
                } else {
                    mismatch = "did not contain exactly the expected elements";
                }
                if (content == Content.ANY_ORDER) {
                    mismatch += " in any order";
                }
                yield mismatch;
            }
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
