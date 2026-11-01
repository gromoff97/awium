package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

record InCondition<EXPECTED>(List<? extends EXPECTED> expected, boolean negated) implements ComparisonCondition<EXPECTED> {
    InCondition(Iterable<? extends EXPECTED> expected, boolean negated) {
        this(stream(requireNonNull(expected, (negated ? "unexpected" : "expected") + " values must not be null").spliterator(), false).toList(), negated);
    }

    @Override public String description() { return negated ? "value is not in the unexpected values" : "value is in the expected values"; }
    @Override public List<Reference<?>> references() { return List.of(new Reference<>(negated ? "Unexpected" : "Expected", expected)); }

    @Override
    public AttemptResult<?> probe(Object actual) {
        return expected.stream().anyMatch(candidate -> deepEquals(actual, candidate)) != negated ? satisfied(actual)
                : unsatisfied(negated ? "value was in the unexpected values" : "value was not in the expected values");
    }
}
