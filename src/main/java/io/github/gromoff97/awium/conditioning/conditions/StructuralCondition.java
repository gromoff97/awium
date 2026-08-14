package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.literalExplanation;
import static java.util.Objects.requireNonNull;

public final class StructuralCondition {

    public static final StructuralCondition empty = new StructuralCondition(Relation.EMPTY, 0);
    public static final StructuralCondition nonEmpty = new StructuralCondition(Relation.NON_EMPTY, 0);

    private final Relation relation;
    private final int bound;

    private StructuralCondition(Relation relation, int bound) {
        this.relation = relation;
        if (bound < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        this.bound = bound;
    }

    public ExplainedCondition because(String explanation) {
        return new ExplainedCondition(this, explanation);
    }

    public ExplainedCondition because(String format, Object... arguments) {
        return new ExplainedCondition(this, formattedExplanation(format, arguments));
    }

    <S> Evaluation<S> evaluate(int size, S actual, String subject) {
        return relation.matches(size, bound)
                ? satisfied(actual)
                : unsatisfied(relation.mismatch(subject, size));
    }

    String description(String subject) {
        return relation.description(subject, bound);
    }

    public static StructuralCondition sizeExactly(int expected) {
        return new StructuralCondition(Relation.EXACTLY, expected);
    }

    public static StructuralCondition sizeNotExactly(int unexpected) {
        return new StructuralCondition(Relation.NOT_EXACTLY, unexpected);
    }

    public static StructuralCondition sizeGreaterThan(int lowerBound) {
        return new StructuralCondition(Relation.GREATER_THAN, lowerBound);
    }

    public static StructuralCondition sizeAtLeast(int lowerBound) {
        return new StructuralCondition(Relation.AT_LEAST, lowerBound);
    }

    public static StructuralCondition sizeLessThan(int upperBound) {
        return new StructuralCondition(Relation.LESS_THAN, upperBound);
    }

    public static StructuralCondition sizeAtMost(int upperBound) {
        return new StructuralCondition(Relation.AT_MOST, upperBound);
    }

    public record ExplainedCondition(StructuralCondition delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }

    private enum Relation {
        EMPTY,
        NON_EMPTY,
        EXACTLY,
        NOT_EXACTLY,
        GREATER_THAN,
        AT_LEAST,
        LESS_THAN,
        AT_MOST;

        private boolean matches(int size, int bound) {
            return switch (this) {
                case EMPTY -> size == 0;
                case NON_EMPTY -> size > 0;
                case EXACTLY -> size == bound;
                case NOT_EXACTLY -> size != bound;
                case GREATER_THAN -> size > bound;
                case AT_LEAST -> size >= bound;
                case LESS_THAN -> size < bound;
                case AT_MOST -> size <= bound;
            };
        }

        private String description(String subject, int bound) {
            return subject + switch (this) {
                case EMPTY -> " is empty";
                case NON_EMPTY -> " is not empty";
                case EXACTLY -> " size is exactly " + bound;
                case NOT_EXACTLY -> " size is not exactly " + bound;
                case GREATER_THAN -> " size is greater than " + bound;
                case AT_LEAST -> " size is at least " + bound;
                case LESS_THAN -> " size is less than " + bound;
                case AT_MOST -> " size is at most " + bound;
            };
        }

        private String mismatch(String subject, int size) {
            return switch (this) {
                case EMPTY -> subject + " was non-empty";
                case NON_EMPTY -> subject + " was empty";
                default -> subject + " size was " + size;
            };
        }
    }
}
