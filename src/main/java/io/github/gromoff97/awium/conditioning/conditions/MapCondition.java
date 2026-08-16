package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Map;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.literalExplanation;
import static java.util.Objects.requireNonNull;

public final class MapCondition {

    public static final MapCondition empty = new MapCondition(size -> size == 0, " is empty", " was non-empty");
    public static final MapCondition nonEmpty = new MapCondition(size -> size > 0, " is not empty", " was empty");

    private final IntPredicate matches;
    private final String expectation;
    private final String fixedMismatch;

    private MapCondition(IntPredicate matches, String expectation, String fixedMismatch) {
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

    public Evaluation<Map<?, ?>> evaluate(Map<?, ?> actual) {
        if (actual == null) {
            return unsatisfied("map was null");
        }
        int size = actual.size();
        return matches.test(size)
                ? satisfied(actual)
                : unsatisfied("map" + (fixedMismatch == null ? " size was " + size : fixedMismatch));
    }

    public String description() {
        return "map" + expectation;
    }

    public static MapCondition sizeExactly(int expected) {
        return sized(expected, size -> size == expected, "is exactly");
    }

    public static MapCondition sizeNotExactly(int unexpected) {
        return sized(unexpected, size -> size != unexpected, "is not exactly");
    }

    public static MapCondition sizeGreaterThan(int lowerBound) {
        return sized(lowerBound, size -> size > lowerBound, "is greater than");
    }

    public static MapCondition sizeAtLeast(int lowerBound) {
        return sized(lowerBound, size -> size >= lowerBound, "is at least");
    }

    public static MapCondition sizeLessThan(int upperBound) {
        return sized(upperBound, size -> size < upperBound, "is less than");
    }

    public static MapCondition sizeAtMost(int upperBound) {
        return sized(upperBound, size -> size <= upperBound, "is at most");
    }

    private static MapCondition sized(int bound, IntPredicate matches, String relation) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        return new MapCondition(matches, " size " + relation + " " + bound, null);
    }

    public record ExplainedCondition(MapCondition delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
