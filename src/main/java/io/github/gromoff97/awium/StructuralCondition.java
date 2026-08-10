package io.github.gromoff97.awium;

import java.util.Objects;

public final class StructuralCondition {

    private final StructuralConditions.Relation relation;
    private final int bound;

    StructuralCondition(StructuralConditions.Relation relation, int bound) {
        this.relation = Objects.requireNonNull(relation);
        this.bound = bound;
    }

    public final Explained because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final Explained because(
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

    public static final class Explained {

        private final StructuralCondition delegate;
        private final String explanation;

        Explained(StructuralCondition delegate, String explanation) {
            this.delegate = Objects.requireNonNull(delegate);
            this.explanation = Objects.requireNonNull(explanation);
        }

        StructuralCondition delegate() {
            return delegate;
        }

        String explanation() {
            return explanation;
        }
    }
}
