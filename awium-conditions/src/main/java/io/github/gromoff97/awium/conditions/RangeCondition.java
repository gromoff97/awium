package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.List;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

final class RangeCondition<BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>> extends PreservingCondition<INPUT> {
    RangeCondition(BOUND lower, BOUND upper, boolean inclusive) {
        super(inclusive ? "value is between the inclusive bounds" : "value is strictly between the bounds",
                new Reference<>("Expected", bounds(lower, upper)), actual -> actual == null
                        ? unsatisfied("value was null")
                        : (inclusive ? actual.compareTo(lower) >= 0 && actual.compareTo(upper) <= 0
                                     : actual.compareTo(lower) > 0 && actual.compareTo(upper) < 0)
                                ? satisfied(actual) : unsatisfied(inclusive ? "value was outside the inclusive range"
                                        : "value was outside the exclusive range"));
    }

    private static <BOUND extends Comparable<? super BOUND>> List<BOUND> bounds(BOUND lower, BOUND upper) {
        requireNonNull(lower, "lower bound must not be null");
        requireNonNull(upper, "upper bound must not be null");
        if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("lower bound must not be greater than upper bound");
        return List.of(lower, upper);
    }

}
