package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.conditions.common.CommonConditions;
import io.github.gromoff97.awium.dsl.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

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
    public AttemptResult<?> probe(CharSequence actual) throws Throwable {
        if (actual == null) return unsatisfied("string was null");
        int length = actual.length();
        var diagnostic = new Diagnostic(null, new Reference<>("Actual length", length));
        if (comparison.probe(length) instanceof Satisfied<?>) {
            return satisfied(actual, diagnostic);
        }
        return unsatisfied(diagnostic.withMismatch("string length did not match"));
    }
}
