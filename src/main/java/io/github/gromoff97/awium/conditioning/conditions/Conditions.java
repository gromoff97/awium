package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
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
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preserve;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preserving;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preservingNonNull;
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

    public static PreservingCondition<Object> equalTo(Object expected) {
        return preserving("value equals expected", "value was not equal",
                actual -> equal(actual, expected));
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return preserving("value does not equal unexpected", "value was equal",
                actual -> !equal(actual, unexpected));
    }

    public static PreservingCondition<Object> sameAs(Object expected) {
        return preserving("value is the same instance", "value was a different instance",
                actual -> actual == expected);
    }

    public static PreservingCondition<Object> notSameAs(Object unexpected) {
        return preserving("value is not the same instance", "value was the same instance",
                actual -> actual != unexpected);
    }

    public static <R> Condition<Object, R> instanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("value is an instance of " + type.getTypeName(), actual ->
                actual != null && type.isInstance(actual)
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not an instance of " + type.getTypeName()));
    }

    public static <R> Condition<Object, R> exactInstanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("value is exactly an instance of " + type.getTypeName(), actual ->
                actual != null && actual.getClass() == type
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not exactly an instance of " + type.getTypeName()));
    }

    public static PreservingCondition<Object> in(Object... expected) {
        List<Object> values = asList(nonEmpty(expected, "expected values"));
        return preserving("value is in the expected values", "value was not in the expected values",
                actual -> matchesAny(values, candidate -> equal(actual, candidate)));
    }

    public static PreservingCondition<Object> notIn(Object... unexpected) {
        List<Object> values = asList(nonEmpty(unexpected, "unexpected values"));
        return preserving("value is not in the unexpected values", "value was in the unexpected values",
                actual -> !matchesAny(values, candidate -> equal(actual, candidate)));
    }

    public static <S> PreservingCondition<S> matches(Predicate<? super S> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("value matches", "value did not match", predicate);
    }

    public static <S, T, R> Condition<S, R> extracting(Function<? super S, ? extends T> extractor,
            ResultStage<? super T, ? extends R> nested) {
        requireNonNull(extractor, "extractor must not be null");
        return ConditionRuntime.condition("extracted " + nested.description(), () -> {
            var nestedEvaluator = nested.newEvaluator();
            return actual -> nestedEvaluator.apply(extractor.apply(actual));
        });
    }

    public static <S, T> Condition<S, T> extracting(Function<? super S, ? extends T> extractor,
            PreservingStage<? super T> nested) {
        return extracting(extractor, preserve(nested));
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
