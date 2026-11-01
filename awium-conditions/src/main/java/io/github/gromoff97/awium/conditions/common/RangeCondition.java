package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record RangeCondition<BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>>(
        BOUND lower, BOUND upper, boolean inclusive) implements PreservingCondition<INPUT> {
    RangeCondition {
        requireNonNull(lower, "lower bound must not be null");
        requireNonNull(upper, "upper bound must not be null");
        if (lower.compareTo(upper) > 0) throw new IllegalArgumentException("lower bound must not be greater than upper bound");
    }

    @Override
    public String description() {
        if (inclusive) {
            return "value is between the inclusive bounds";
        }
        return "value is strictly between the bounds";
    }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>("Expected", List.of(lower, upper))); }

    @Override
    public AttemptResult<?> probe(INPUT actual) {
        if (actual == null) {
            return unsatisfied("value was null");
        }
        if (inclusive) {
            if (actual.compareTo(lower) >= 0 && actual.compareTo(upper) <= 0) {
                return satisfied(actual);
            }
            return unsatisfied("value was outside the inclusive range");
        }
        if (actual.compareTo(lower) > 0 && actual.compareTo(upper) < 0) {
            return satisfied(actual);
        }
        return unsatisfied("value was outside the exclusive range");
    }
}
