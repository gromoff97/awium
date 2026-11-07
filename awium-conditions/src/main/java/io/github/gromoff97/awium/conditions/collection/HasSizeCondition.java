package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.conditions.common.CommonConditions;
import io.github.gromoff97.awium.dsl.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

final class HasSizeCondition implements PreservingCondition<Collection<?>> {
    private final ApplicableCondition<? super Integer, ?> comparison;

    HasSizeCondition(int bound, Function<Integer, ? extends ApplicableCondition<? super Integer, ?>> factory) {
        if (bound < 0) throw new IllegalArgumentException("size must be non-negative");
        comparison = factory.apply(bound);
    }

    HasSizeCondition(int lower, int upper) {
        if (lower < 0 || upper < lower) throw new IllegalArgumentException("size range must be non-negative and ordered");
        comparison = CommonConditions.between(lower, upper);
    }

    @Override public String description() { return "collection size: " + comparison.description(); }
    @Override public List<Reference<?>> references() { return comparison.references(); }

    @Override
    public AttemptResult<?> probe(Collection<?> actual) throws Throwable {
        if (actual == null) return unsatisfied("collection was null");
        int size = actual.size();
        var diagnostic = new Diagnostic(null, new Reference<>("Actual size", size));
        if (comparison.probe(size) instanceof Satisfied<?>) {
            return satisfied(actual, diagnostic);
        }
        return unsatisfied(diagnostic.withMismatch("collection size did not match"));
    }
}
