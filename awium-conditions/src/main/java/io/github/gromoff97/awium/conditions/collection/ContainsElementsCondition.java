package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.Collection;
import java.util.List;

import static io.github.gromoff97.awium.conditions.collection.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.firstMissingExpectedIndex;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.firstPresentExpectedIndex;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Locale.ROOT;

record ContainsElementsCondition<ELEMENT>(List<? extends ELEMENT> expected, Membership membership)
        implements PreservingCondition<Collection<? super ELEMENT>> {
    enum Membership {
        ALL("Expected", "contains all expected elements"),
        ANY("Expected", "contains an expected element"),
        NONE("Unexpected", "does not contain any unexpected elements"),
        NOT_ALL("Unexpected", "does not contain all unexpected elements");

        private final String operand;
        private final String description;

        Membership(String operand, String description) {
            this.operand = operand;
            this.description = description;
        }
    }

    ContainsElementsCondition(Iterable<? extends ELEMENT> expected, Membership membership) {
        this(snapshot(expected, membership.operand.toLowerCase(ROOT) + " elements"), membership);
    }

    @Override public String description() {
        return "collection " + membership.description;
    }

    @Override public List<Reference<?>> references() { return List.of(new Reference<>(membership.operand, expected)); }

    @Override public AttemptResult<?> probe(Collection<? super ELEMENT> actual) {
        if (actual == null) return unsatisfied("collection was null");
        int index;
        if (membership == Membership.ALL || membership == Membership.NOT_ALL) {
            index = firstMissingExpectedIndex(actual, expected);
        } else {
            index = firstPresentExpectedIndex(actual, expected);
        }
        boolean matched;
        if (membership == Membership.ALL || membership == Membership.NONE) {
            matched = index < 0;
        } else {
            matched = index >= 0;
        }
        if (matched) {
            return satisfied(actual);
        }
        return unsatisfied("collection " + switch (membership) {
            case ALL -> "did not contain all expected elements; first missing at expected index " + index;
            case NONE -> "contained unexpected elements; first at unexpected index " + index;
            case NOT_ALL -> "contained all unexpected elements";
            case ANY -> "did not contain an expected element";
        });
    }
}
