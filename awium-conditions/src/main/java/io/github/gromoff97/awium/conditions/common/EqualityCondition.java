package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

final class EqualityCondition<EXPECTED> extends ComparisonCondition<EXPECTED> {
    EqualityCondition(EXPECTED expected, boolean sameInstance, boolean negated) {
        super(sameInstance ? negated ? "value is not the same instance" : "value is the same instance"
                        : negated ? "value does not equal unexpected" : "value equals expected",
                new Reference<>(negated ? "Unexpected" : "Expected", expected), actual ->
                        (sameInstance ? actual == expected : deepEquals(actual, expected)) != negated
                                ? satisfied(actual) : unsatisfied(sameInstance
                                        ? negated ? "value was the same instance" : "value was a different instance"
                                        : negated ? "value was equal" : "value was not equal"));
    }

}
