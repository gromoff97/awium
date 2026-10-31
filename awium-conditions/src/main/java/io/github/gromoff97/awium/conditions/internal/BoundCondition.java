package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class BoundCondition<BOUND, INPUT extends Comparable<? super BOUND>> extends PreservingCondition<INPUT> {
    public BoundCondition(String relation, BOUND bound, SizeRelation comparison) {
        super("value is " + relation + " the bound", new Reference<>("Expected", bound), actual -> actual == null
                ? unsatisfied("value was null") : comparison.matches(actual.compareTo(bound), 0, 0)
                        ? satisfied(actual) : unsatisfied("value was not " + relation + " the bound"));
        requireNonNull(bound, "bound must not be null");
    }

}
