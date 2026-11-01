package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditions.common.SizeRelation.BETWEEN;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class HasSizeCondition<INPUT> extends PreservingCondition<INPUT> {
    public HasSizeCondition(String subject, String measure, ToIntFunction<? super INPUT> sizeOf,
            SizeRelation relation, int bound) {
        this(subject, measure, sizeOf, relation, bound, bound);
    }

    public HasSizeCondition(String subject, String measure, ToIntFunction<? super INPUT> sizeOf,
            SizeRelation relation, int lower, int upper) {
        super(subject + " " + measure + " " + relation.description(lower, upper), null, actual -> {
            if (actual == null) return unsatisfied(subject + " was null");
            int size = sizeOf.applyAsInt(actual);
            return relation.matches(size, lower, upper) ? satisfied(actual)
                    : unsatisfied(subject + " " + measure + " was " + size);
        });
        if (relation == BETWEEN && (lower < 0 || upper < lower)) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
        if (lower < 0) throw new IllegalArgumentException(measure + " must be non-negative");
    }

}
