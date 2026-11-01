package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

record EqualityCondition<EXPECTED>(EXPECTED expected, boolean sameInstance, boolean negated) implements ComparisonCondition<EXPECTED> {
    @Override
    public String description() {
        return sameInstance ? negated ? "value is not the same instance" : "value is the same instance"
                : negated ? "value does not equal unexpected" : "value equals expected";
    }

    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", expected)); }

    @Override
    public ConditionResult<?> probe(Object actual) {
        return (sameInstance ? actual == expected : deepEquals(actual, expected)) != negated
                ? satisfied(actual) : unsatisfied(sameInstance
                        ? negated ? "value was the same instance" : "value was a different instance"
                        : negated ? "value was equal" : "value was not equal");
    }
}
