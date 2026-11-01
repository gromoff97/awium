package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;

record EqualityCondition<EXPECTED>(EXPECTED expected, boolean sameInstance, boolean negated) implements ComparisonCondition<EXPECTED> {
    @Override
    public String description() {
        if (sameInstance) {
            if (negated) {
                return "value is not the same instance";
            }
            return "value is the same instance";
        }
        if (negated) {
            return "value does not equal unexpected";
        }
        return "value equals expected";
    }

    @Override
    public List<Reference<?>> references() {
        if (negated) {
            return List.of(new Reference<>("Unexpected", expected));
        }
        return List.of(new Reference<>("Expected", expected));
    }

    @Override
    public AttemptResult<?> probe(Object actual) {
        boolean matches;
        if (sameInstance) {
            matches = actual == expected;
        } else {
            matches = deepEquals(actual, expected);
        }
        if (matches != negated) {
            return satisfied(actual);
        }
        if (sameInstance) {
            if (negated) {
                return unsatisfied("value was the same instance");
            }
            return unsatisfied("value was a different instance");
        }
        if (negated) {
            return unsatisfied("value was equal");
        }
        return unsatisfied("value was not equal");
    }
}
