package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record RangeCondition<BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>>(
        BOUND lower, BOUND upper, boolean inclusive) implements PreservingCondition<INPUT> {
    RangeCondition {
        requireNonNull(lower, "lower bound must not be null");
        requireNonNull(upper, "upper bound must not be null");
        if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("lower bound must not be greater than upper bound");
    }

    @Override public String description() { return inclusive ? "value is between the inclusive bounds" : "value is strictly between the bounds"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>("Expected", List.of(lower, upper))); }

    @Override
    public ConditionResult<?> probe(INPUT actual) {
        return actual == null ? unsatisfied("value was null")
                : (inclusive ? actual.compareTo(lower) >= 0 && actual.compareTo(upper) <= 0
                             : actual.compareTo(lower) > 0 && actual.compareTo(upper) < 0)
                        ? satisfied(actual) : unsatisfied(inclusive ? "value was outside the inclusive range"
                                : "value was outside the exclusive range");
    }
}
