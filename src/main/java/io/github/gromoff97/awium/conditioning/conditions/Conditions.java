package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedSequenceCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.NarrowingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preserving;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.expected;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.narrowing;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAny;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public final class Conditions {

    public static final Condition<Object, Void> isNull = condition("value is null",
            actual -> actual == null ? satisfied(null) : unsatisfied("value was not null"));
    public static final PreservingCondition<Object> isNotNull = preserving("value is not null",
            "value was null", actual -> actual != null);

    private Conditions() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> Condition<S, R> condition(String description,
            Function<? super S, Evaluation<R>> evaluation) {
        return ConditionRuntime.condition(description, evaluation);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S> Condition<S, List<S>> captured(Predicate<? super S> first,
            Predicate<? super S> second, Predicate<? super S>... rest) {
        return ConditionRuntime.captured(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S> Condition<S, List<S>> captured(PreservingStage<? super S> first,
            PreservingStage<? super S> second, PreservingStage<? super S>... rest) {
        return ConditionRuntime.captured(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, R> Condition<S, List<R>> captured(ResultStage<S, R> first,
            ResultStage<S, R> second, ResultStage<S, R>... rest) {
        return ConditionRuntime.captured(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> ExpectedSequenceCondition<T> captured(ExpectedStage<? extends T> first,
            ExpectedStage<? extends T> second, ExpectedStage<? extends T>... rest) {
        return ConditionRuntime.captured(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <S, F extends Source<?>> SelectedSequenceCondition<S, F> captured(SelectedStage<? super S, F> first,
            SelectedStage<? super S, F> second, SelectedStage<? super S, F>... rest) {
        return ConditionRuntime.captured(first, second, rest);
    }

    public static <S> PreservingCondition<S> asserted(Consumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return ConditionRuntime.preserving("value satisfies assertion", actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return assertionUnsatisfied("value did not satisfy assertion", error);
            }
        });
    }

    public static <S, R> Condition<S, R> yields(Function<? super S, ? extends R> callback) {
        requireNonNull(callback, "callback must not be null");
        return condition("callback yields a result", actual -> satisfied(callback.apply(actual)));
    }

    public static <T> ExpectedCondition<T> equalTo(T expected) {
        return expected("value equals expected", actual -> equal(actual, expected)
                ? satisfied(actual) : unsatisfied("value was not equal"));
    }

    public static <T> ExpectedCondition<T> notEqualTo(T unexpected) {
        return expected("value does not equal unexpected", actual -> !equal(actual, unexpected)
                ? satisfied(actual) : unsatisfied("value was equal"));
    }

    public static <T> ExpectedCondition<T> sameAs(T expected) {
        return expected("value is the same instance", actual -> actual == expected
                ? satisfied(actual) : unsatisfied("value was a different instance"));
    }

    public static <T> ExpectedCondition<T> notSameAs(T unexpected) {
        return expected("value is not the same instance", actual -> actual != unexpected
                ? satisfied(actual) : unsatisfied("value was the same instance"));
    }

    public static <R> NarrowingCondition<R> instanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return narrowing("value is an instance of " + type.getTypeName(), actual ->
                actual != null && type.isInstance(actual)
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not an instance of " + type.getTypeName()));
    }

    public static <R> NarrowingCondition<R> exactInstanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return narrowing("value is exactly an instance of " + type.getTypeName(), actual ->
                actual != null && actual.getClass() == type
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not exactly an instance of " + type.getTypeName()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> ExpectedCondition<T> in(T... expected) {
        List<T> values = asList(nonEmpty(expected, "expected values"));
        return expected("value is in the expected values", actual -> matchesAny(values, candidate -> equal(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was not in the expected values"));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> ExpectedCondition<T> notIn(T... unexpected) {
        List<T> values = asList(nonEmpty(unexpected, "unexpected values"));
        return expected("value is not in the unexpected values", actual -> !matchesAny(values, candidate -> equal(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was in the unexpected values"));
    }

    public static <S> PreservingCondition<S> matches(Predicate<? super S> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("value matches", "value did not match", predicate);
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
        return comparable("value is between the inclusive bounds", "value was outside the inclusive range",
                actual -> actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0);
    }

    public static <T extends Comparable<? super T>> PreservingCondition<T> strictlyBetween(T lowerBound, T upperBound) {
        validateRange(lowerBound, upperBound);
        return comparable("value is strictly between the bounds", "value was outside the exclusive range",
                actual -> actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0);
    }

    private static <T extends Comparable<? super T>> PreservingCondition<T> comparing(String relation, T bound,
            Predicate<? super T> matches) {
        requireNonNull(bound, "bound must not be null");
        return comparable("value is " + relation + " the bound",
                "value was not " + relation + " the bound", matches);
    }

    private static <T> PreservingCondition<T> comparable(String description, String mismatch,
            Predicate<? super T> matches) {
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
