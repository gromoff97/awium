package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static io.github.gromoff97.awium.conditions.internal.CollectionSequenceCondition.prefixMatches;
import static io.github.gromoff97.awium.conditions.internal.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.matchesAll;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.sameDistinctElements;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.sameElementsInAnyOrder;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class CollectionContentCondition<INPUT extends Collection<?>> extends PreservingCondition<INPUT> {
    public enum Content { EXACT, ANY_ORDER, ONLY, SUBSET }

    public CollectionContentCondition(Iterable<?> expected, Content content, boolean negated) {
        this(snapshot(expected, negated ? "unexpected elements" : "expected elements"), content, negated);
    }

    private CollectionContentCondition(List<?> expected, Content content, boolean negated) {
        super("collection " + switch (content) {
            case EXACT, ANY_ORDER -> (negated ? "does not contain exactly the unexpected elements" : "contains exactly the expected elements")
                    + (content == Content.ANY_ORDER ? " in any order" : "");
            case ONLY -> "contains only the expected elements";
            case SUBSET -> "is a subset of the expected elements";
        }, new Reference<>(negated ? "Unexpected" : "Expected", expected), actual -> {
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
        });
    }

    private static boolean exactContent(Collection<?> actual, Collection<?> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Iterator<?> values = actual.iterator();
        return prefixMatches(values, expected) && !values.hasNext();
    }

    private static boolean exactContentInAnyOrder(Collection<?> actual, Collection<?> expected) throws Throwable {
        return actual.size() == expected.size()
                && sameElementsInAnyOrder(actual.iterator(), expected, Objects::deepEquals);
    }

}
