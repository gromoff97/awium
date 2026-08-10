package io.github.gromoff97.awium;

import java.util.Objects;

public final class StructuralCondition {

    private final StructuralConditions.Relation relation;
    private final int bound;

    StructuralCondition(StructuralConditions.Relation relation, int bound) {
        this.relation = Objects.requireNonNull(relation);
        this.bound = bound;
    }

    public final ExplainedStructuralCondition because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final ExplainedStructuralCondition because(
            String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    <S> Evaluation<S> evaluate(int size, S actual, String subject) {
        return relation.matches(size, bound)
                ? Evaluation.satisfied(actual)
                : Evaluation.unsatisfied(relation.mismatch(subject, size));
    }

    String description(String subject) {
        return relation.description(subject, bound);
    }
}
