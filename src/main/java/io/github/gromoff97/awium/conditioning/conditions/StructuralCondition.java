package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.literalExplanation;
import static java.util.Objects.requireNonNull;

public final class StructuralCondition {

    public static final StructuralCondition empty = new StructuralCondition(size -> size == 0, " is empty", " was non-empty");
    public static final StructuralCondition nonEmpty = new StructuralCondition(size -> size > 0, " is not empty", " was empty");

    private final IntPredicate matches;
    private final String expectation;
    private final String fixedMismatch;

    private StructuralCondition(IntPredicate matches, String expectation, String fixedMismatch) {
        this.matches = matches;
        this.expectation = expectation;
        this.fixedMismatch = fixedMismatch;
    }

    public ExplainedCondition because(String explanation) {
        return new ExplainedCondition(this, explanation);
    }

    public ExplainedCondition because(String format, Object... arguments) {
        return new ExplainedCondition(this, formattedExplanation(format, arguments));
    }

    public <S> Evaluation<S> evaluate(int size, S actual, String subject) {
        return matches.test(size)
                ? satisfied(actual)
                : unsatisfied(subject + (fixedMismatch == null ? " size was " + size : fixedMismatch));
    }

    public String description(String subject) {
        return subject + expectation;
    }

    public static StructuralCondition sizeExactly(int expected) {
        return sized(expected, size -> size == expected, "is exactly");
    }

    public static StructuralCondition sizeNotExactly(int unexpected) {
        return sized(unexpected, size -> size != unexpected, "is not exactly");
    }

    public static StructuralCondition sizeGreaterThan(int lowerBound) {
        return sized(lowerBound, size -> size > lowerBound, "is greater than");
    }

    public static StructuralCondition sizeAtLeast(int lowerBound) {
        return sized(lowerBound, size -> size >= lowerBound, "is at least");
    }

    public static StructuralCondition sizeLessThan(int upperBound) {
        return sized(upperBound, size -> size < upperBound, "is less than");
    }

    public static StructuralCondition sizeAtMost(int upperBound) {
        return sized(upperBound, size -> size <= upperBound, "is at most");
    }

    private static StructuralCondition sized(int bound, IntPredicate matches, String relation) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        return new StructuralCondition(matches, " size " + relation + " " + bound, null);
    }

    public record ExplainedCondition(StructuralCondition delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }

}
