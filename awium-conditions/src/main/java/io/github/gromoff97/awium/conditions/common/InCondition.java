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
        this(snapshot(expected, negated), negated);
    }

    @Override
    public String description() {
        if (negated) {
            return "value is not in the unexpected values";
        }
        return "value is in the expected values";
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
        if (expected.stream().anyMatch(candidate -> deepEquals(actual, candidate)) != negated) {
            return satisfied(actual);
        }
        if (negated) {
            return unsatisfied("value was in the unexpected values");
        }
        return unsatisfied("value was not in the expected values");
    }

    private static <EXPECTED> List<EXPECTED> snapshot(Iterable<EXPECTED> expected, boolean negated) {
        if (negated) {
            requireNonNull(expected, "unexpected values must not be null");
        } else {
            requireNonNull(expected, "expected values must not be null");
        }
        return stream(expected.spliterator(), false).toList();
    }
}
