package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedPredicate;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;

import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preservingNonNull;
import static java.util.Objects.requireNonNull;

public final class ComparableCondition {

    private ComparableCondition() {
        throw new AssertionError("Utility class");
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> greaterThan(T bound) {
        return comparing("greater than", bound, actual -> actual.compareTo(bound) > 0);
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> atLeast(T bound) {
        return comparing("at least", bound, actual -> actual.compareTo(bound) >= 0);
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> lessThan(T bound) {
        return comparing("less than", bound, actual -> actual.compareTo(bound) < 0);
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> atMost(T bound) {
        return comparing("at most", bound, actual -> actual.compareTo(bound) <= 0);
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> between(T lowerBound, T upperBound) {
        validateRange(lowerBound, upperBound);
        return preserving("value is between the inclusive bounds",
                "value was outside the inclusive range",
                actual -> actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0);
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> strictlyBetween(T lowerBound, T upperBound) {
        validateRange(lowerBound, upperBound);
        return preserving("value is strictly between the bounds",
                "value was outside the exclusive range",
                actual -> actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0);
    }

    private static <T extends Comparable<? super T>> PreservingCondition<T> comparing(String relation, T bound,
            CheckedPredicate<? super T> matches) {
        requireNonNull(bound, "bound must not be null");
        return preserving("value is " + relation + " the bound",
                "value was not " + relation + " the bound", matches);
    }

    private static <T> PreservingCondition<T> preserving(String description, String mismatch,
            CheckedPredicate<? super T> matches) {
        return preservingNonNull("value", description, mismatch, matches);
    }

    private static <T extends Comparable<? super T>> void validateRange(T lowerBound, T upperBound) {
        requireNonNull(lowerBound, "lower bound must not be null");
        requireNonNull(upperBound, "upper bound must not be null");
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("lower bound must not be greater than upper bound");
        }
    }
}
