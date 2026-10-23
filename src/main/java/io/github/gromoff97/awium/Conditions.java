package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.CheckedCalls.checkInterruption;
import static io.github.gromoff97.awium.ConditionResult.assertionUnsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ValueMatching.equal;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.ValueRendering.snapshot;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

public final class Conditions {

    public static final Condition<Object, Void> isNull = ConditionSupport.condition("value is null", null,
            actual -> actual == null ? satisfied(null) : unsatisfied("value was not null"));
    public static final PreservingCondition<Object> isNotNull = ConditionSupport.preserving("value is not null",
            "value was null", actual -> actual != null);

    private Conditions() {}

    public static <OBSERVED> PreservingCondition<OBSERVED> condition(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>> evaluation) {
        return ConditionSupport.preserving(description, evaluation);
    }

    public static <OBSERVED> PreservingCondition<OBSERVED> condition(String description,
            Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory) {
        return new PreservingCondition<>(description, null, evaluatorFactory);
    }

    public static <OBSERVED> PreservingCondition<OBSERVED> asserted(CheckedConsumer<? super OBSERVED> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return ConditionSupport.preserving("value satisfies assertion", actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return assertionUnsatisfied("value did not satisfy assertion", error);
            }
        });
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> equalTo(EXPECTED expected) {
        return new ComparisonCondition<>(ConditionSupport.preserving("value equals expected", expectedReference(expected), actual -> equal(actual, expected)
                ? satisfied(actual) : unsatisfied("value was not equal")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notEqualTo(EXPECTED unexpected) {
        return new ComparisonCondition<>(ConditionSupport.preserving("value does not equal unexpected", unexpectedReference(unexpected), actual -> !equal(actual, unexpected)
                ? satisfied(actual) : unsatisfied("value was equal")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> sameAs(EXPECTED expected) {
        return new ComparisonCondition<>(ConditionSupport.preserving("value is the same instance", expectedReference(expected), actual -> actual == expected
                ? satisfied(actual) : unsatisfied("value was a different instance")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notSameAs(EXPECTED unexpected) {
        return new ComparisonCondition<>(ConditionSupport.preserving("value is not the same instance", unexpectedReference(unexpected), actual -> actual != unexpected
                ? satisfied(actual) : unsatisfied("value was the same instance")));
    }

    public static <RESULT> Condition<Object, RESULT> instanceOf(Class<RESULT> type) {
        requireNonNull(type, "type must not be null");
        return ConditionSupport.condition("value is an instance of " + type.getTypeName(), null, actual ->
                actual != null && type.isInstance(actual)
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not an instance of " + type.getTypeName()));
    }

    public static <RESULT> Condition<Object, RESULT> exactInstanceOf(Class<RESULT> type) {
        requireNonNull(type, "type must not be null");
        return ConditionSupport.condition("value is exactly an instance of " + type.getTypeName(), null, actual ->
                actual != null && actual.getClass() == type
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not exactly an instance of " + type.getTypeName()));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <EXPECTED> ComparisonCondition<EXPECTED> in(EXPECTED... expected) {
        List<?> values = snapshot(stream(nonEmpty(expected, "expected values")));
        return new ComparisonCondition<>(ConditionSupport.preserving("value is in the expected values", expectedReference(values),
                actual -> matchesAny(values, candidate -> equal(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was not in the expected values")));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <EXPECTED> ComparisonCondition<EXPECTED> notIn(EXPECTED... unexpected) {
        List<?> values = snapshot(stream(nonEmpty(unexpected, "unexpected values")));
        return new ComparisonCondition<>(ConditionSupport.preserving("value is not in the unexpected values", unexpectedReference(values),
                actual -> !matchesAny(values, candidate -> equal(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was in the unexpected values")));
    }

    public static <OBSERVED> PreservingCondition<OBSERVED> matches(Predicate<? super OBSERVED> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return ConditionSupport.preserving("value matches", "value did not match", predicate::test);
    }

    public static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> greaterThan(VALUE bound) {
        return comparing("greater than", bound, actual -> compare(actual, bound) > 0);
    }

    public static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> atLeast(VALUE bound) {
        return comparing("at least", bound, actual -> compare(actual, bound) >= 0);
    }

    public static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> lessThan(VALUE bound) {
        return comparing("less than", bound, actual -> compare(actual, bound) < 0);
    }

    public static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> atMost(VALUE bound) {
        return comparing("at most", bound, actual -> compare(actual, bound) <= 0);
    }

    public static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> between(VALUE lowerBound, VALUE upperBound) {
        validateRange(lowerBound, upperBound);
        return preservingNonNull("value", "value is between the inclusive bounds", "value was outside the inclusive range",
                expectedReference(snapshot(Stream.of(lowerBound, upperBound))),
                actual -> compare(actual, lowerBound) >= 0 && compare(actual, upperBound) <= 0);
    }

    public static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> strictlyBetween(VALUE lowerBound, VALUE upperBound) {
        validateRange(lowerBound, upperBound);
        return preservingNonNull("value", "value is strictly between the bounds", "value was outside the exclusive range",
                expectedReference(snapshot(Stream.of(lowerBound, upperBound))),
                actual -> compare(actual, lowerBound) > 0 && compare(actual, upperBound) < 0);
    }

    private static <VALUE extends Comparable<? super VALUE>> PreservingCondition<VALUE> comparing(String relation, VALUE bound,
            CheckedFunction<? super VALUE, Boolean> matches) {
        requireNonNull(bound, "bound must not be null");
        return preservingNonNull("value", "value is " + relation + " the bound",
                "value was not " + relation + " the bound", expectedReference(bound), matches);
    }

    private static <VALUE extends Comparable<? super VALUE>> int compare(VALUE actual, VALUE bound) throws InterruptedException {
        checkInterruption();
        return actual.compareTo(bound);
    }

    private static <VALUE extends Comparable<? super VALUE>> void validateRange(VALUE lowerBound, VALUE upperBound) {
        requireNonNull(lowerBound, "lower bound must not be null");
        requireNonNull(upperBound, "upper bound must not be null");
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("lower bound must not be greater than upper bound");
        }
    }

}
