package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.conditions.common.CommonConditions;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

final class HasLengthCondition implements PreservingCondition<CharSequence> {
    private final ApplicableCondition<? super Integer, ?> comparison;

    HasLengthCondition(int bound, Function<Integer, ? extends ApplicableCondition<? super Integer, ?>> factory) {
        if (bound < 0) throw new IllegalArgumentException("length must be non-negative");
        comparison = factory.apply(bound);
    }

    HasLengthCondition(int lower, int upper) {
        if (lower < 0 || upper < lower) throw new IllegalArgumentException("length range must be non-negative and ordered");
        comparison = CommonConditions.between(lower, upper);
    }

    @Override public String description() { return "string length: " + comparison.description(); }
    @Override public List<Reference<?>> references() { return comparison.references(); }

    @Override
    public ConditionResult<?> probe(CharSequence actual) throws Throwable {
        if (actual == null) return unsatisfied("string was null");
        int length = actual.length();
        return comparison.probe(length) instanceof Satisfied<?> ? satisfied(actual) : unsatisfied("string length was " + length);
    }
}
