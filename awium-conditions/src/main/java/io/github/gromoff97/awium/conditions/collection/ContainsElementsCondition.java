package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Collection;
import java.util.List;

import static io.github.gromoff97.awium.conditions.collection.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.firstMissingExpectedIndex;
import static io.github.gromoff97.awium.conditions.collection.ValueMatching.firstPresentExpectedIndex;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Locale.ROOT;

record ContainsElementsCondition<ELEMENT>(List<? extends ELEMENT> expected, Membership membership)
        implements PreservingCondition<Collection<? super ELEMENT>> {
    enum Membership {
        ALL, ANY, NONE, NOT_ALL;
        String operand() { return this == NONE || this == NOT_ALL ? "Unexpected" : "Expected"; }
    }

    ContainsElementsCondition(Iterable<? extends ELEMENT> expected, Membership membership) {
        this(snapshot(expected, membership.operand().toLowerCase(ROOT) + " elements"), membership);
    }

    @Override public String description() {
        return "collection " + switch (membership) {
            case ALL -> "contains all expected elements";
            case ANY -> "contains an expected element";
            case NONE -> "does not contain any unexpected elements";
            case NOT_ALL -> "does not contain all unexpected elements";
        };
    }

    @Override public List<Reference<?>> references() { return List.of(new Reference<>(membership.operand(), expected)); }

    @Override public ConditionResult<?> probe(Collection<? super ELEMENT> actual) {
        if (actual == null) return unsatisfied("collection was null");
        int index = membership == Membership.ALL || membership == Membership.NOT_ALL
                ? firstMissingExpectedIndex(actual, expected) : firstPresentExpectedIndex(actual, expected);
        boolean matched = membership == Membership.ALL || membership == Membership.NONE ? index < 0 : index >= 0;
        return matched ? satisfied(actual) : unsatisfied("collection " + switch (membership) {
            case ALL -> "did not contain all expected elements; first missing at expected index " + index;
            case NONE -> "contained unexpected elements; first at unexpected index " + index;
            case NOT_ALL -> "contained all unexpected elements";
            case ANY -> "did not contain an expected element";
        });
    }
}
