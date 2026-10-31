package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import java.util.List;

import static io.github.gromoff97.awium.conditions.internal.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.deepEquals;

public final class InCondition<EXPECTED> extends ComparisonCondition<EXPECTED> {
    public InCondition(Iterable<? extends EXPECTED> expected, boolean negated) {
        this(snapshot(expected, negated ? "unexpected values" : "expected values"), negated);
    }

    private InCondition(List<? extends EXPECTED> values, boolean negated) {
        super(negated ? "value is not in the unexpected values" : "value is in the expected values",
                new Reference<>(negated ? "Unexpected" : "Expected", values), actual ->
                        matchesAny(values, candidate -> deepEquals(actual, candidate)) != negated ? satisfied(actual)
                                : unsatisfied(negated ? "value was in the unexpected values" : "value was not in the expected values"));
    }

}
