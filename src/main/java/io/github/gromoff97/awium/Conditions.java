package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;

import io.github.gromoff97.awium.ConditionResult.Reference;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.ConditionResult.assertionUnsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionFactories.expected;
import static io.github.gromoff97.awium.ConditionFactories.expectedReference;
import static io.github.gromoff97.awium.ConditionFactories.narrowing;
import static io.github.gromoff97.awium.ConditionFactories.unexpectedReference;
import static io.github.gromoff97.awium.ValueMatching.equal;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public final class Conditions {

    public static final Condition<Object, Void> isNull = condition("value is null",
            actual -> actual == null ? satisfied(null) : unsatisfied("value was not null"));
    public static final PreservingCondition<Object> isNotNull = ConditionSupport.preserving("value is not null",
            "value was null", actual -> actual != null);

    private Conditions() {
        throw new AssertionError("Utility class");
    }

    public static <Observed, Result> Condition<Observed, Result> condition(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>> evaluation) {
        return ConditionFactories.condition(description, evaluation);
    }

    public static <Observed, Result> Condition<Observed, Result> conditionFactory(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return ConditionFactories.conditionFactory(description, evaluatorFactory);
    }

    public static <Observed> PreservingCondition<Observed> preserving(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>> evaluation) {
        return ConditionFactories.preserving(description, evaluation);
    }

    public static <Observed> PreservingCondition<Observed> preservingFactory(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>>> evaluatorFactory) {
        return ConditionFactories.preserving(description, evaluatorFactory);
    }

    public static <Observed> PreservingCondition<Observed> asserted(CheckedConsumer<? super Observed> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return ConditionFactories.preserving("value satisfies assertion", actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return assertionUnsatisfied("value did not satisfy assertion", error);
            }
        });
    }

    public static <Observed, Result> Condition<Observed, Result> yields(CheckedFunction<? super Observed, ? extends Result> callback) {
        requireNonNull(callback, "callback must not be null");
        return condition("callback yields a result", actual -> satisfied(callback.apply(actual)));
    }

    public static <Value> ExpectedCondition<Value> equalTo(Value expected) {
        return expected("value equals expected", expectedReference(expected), actual -> equal(actual, expected)
                ? satisfied(actual) : unsatisfied("value was not equal"));
    }

    public static <Value> ExpectedCondition<Value> notEqualTo(Value unexpected) {
        return expected("value does not equal unexpected", unexpectedReference(unexpected), actual -> !equal(actual, unexpected)
                ? satisfied(actual) : unsatisfied("value was equal"));
    }

    public static <Value> ExpectedCondition<Value> sameAs(Value expected) {
        return expected("value is the same instance", expectedReference(expected), actual -> actual == expected
                ? satisfied(actual) : unsatisfied("value was a different instance"));
    }

    public static <Value> ExpectedCondition<Value> notSameAs(Value unexpected) {
        return expected("value is not the same instance", unexpectedReference(unexpected), actual -> actual != unexpected
                ? satisfied(actual) : unsatisfied("value was the same instance"));
    }

    public static <Result> NarrowingCondition<Result> instanceOf(Class<Result> type) {
        requireNonNull(type, "type must not be null");
        return narrowing("value is an instance of " + type.getTypeName(), actual ->
                actual != null && type.isInstance(actual)
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not an instance of " + type.getTypeName()));
    }

    public static <Result> NarrowingCondition<Result> exactInstanceOf(Class<Result> type) {
        requireNonNull(type, "type must not be null");
        return narrowing("value is exactly an instance of " + type.getTypeName(), actual ->
                actual != null && actual.getClass() == type
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not exactly an instance of " + type.getTypeName()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Value> ExpectedCondition<Value> in(Value... expected) {
        List<Value> values = asList(nonEmpty(expected, "expected values"));
        return expected("value is in the expected values", expectedReference(values),
                actual -> matchesAny(values, candidate -> equal(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was not in the expected values"));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <Value> ExpectedCondition<Value> notIn(Value... unexpected) {
        List<Value> values = asList(nonEmpty(unexpected, "unexpected values"));
        return expected("value is not in the unexpected values", unexpectedReference(values),
                actual -> !matchesAny(values, candidate -> equal(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was in the unexpected values"));
    }

    public static <Observed> PreservingCondition<Observed> matches(Predicate<? super Observed> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return ConditionSupport.preserving("value matches", "value did not match", predicate);
    }

    public static <Value extends Comparable<? super Value>> PreservingCondition<Value> greaterThan(Value bound) {
        return comparing("greater than", bound, actual -> actual.compareTo(bound) > 0);
    }

    public static <Value extends Comparable<? super Value>> PreservingCondition<Value> atLeast(Value bound) {
        return comparing("at least", bound, actual -> actual.compareTo(bound) >= 0);
    }

    public static <Value extends Comparable<? super Value>> PreservingCondition<Value> lessThan(Value bound) {
        return comparing("less than", bound, actual -> actual.compareTo(bound) < 0);
    }

    public static <Value extends Comparable<? super Value>> PreservingCondition<Value> atMost(Value bound) {
        return comparing("at most", bound, actual -> actual.compareTo(bound) <= 0);
    }

    public static <Value extends Comparable<? super Value>> PreservingCondition<Value> between(Value lowerBound, Value upperBound) {
        validateRange(lowerBound, upperBound);
        return comparable("value is between the inclusive bounds", "value was outside the inclusive range",
                expectedReference(List.of(lowerBound, upperBound)),
                actual -> actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0);
    }

    public static <Value extends Comparable<? super Value>> PreservingCondition<Value> strictlyBetween(Value lowerBound, Value upperBound) {
        validateRange(lowerBound, upperBound);
        return comparable("value is strictly between the bounds", "value was outside the exclusive range",
                expectedReference(List.of(lowerBound, upperBound)),
                actual -> actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0);
    }

    private static <Value extends Comparable<? super Value>> PreservingCondition<Value> comparing(String relation, Value bound,
            Predicate<? super Value> matches) {
        requireNonNull(bound, "bound must not be null");
        return comparable("value is " + relation + " the bound",
                "value was not " + relation + " the bound", expectedReference(bound), matches);
    }

    private static <Value> PreservingCondition<Value> comparable(String description, String mismatch,
            Reference<?> reference, Predicate<? super Value> matches) {
        return preservingNonNull("value", description, mismatch, reference, matches);
    }

    private static <Value extends Comparable<? super Value>> void validateRange(Value lowerBound, Value upperBound) {
        requireNonNull(lowerBound, "lower bound must not be null");
        requireNonNull(upperBound, "upper bound must not be null");
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("lower bound must not be greater than upper bound");
        }
    }

}
