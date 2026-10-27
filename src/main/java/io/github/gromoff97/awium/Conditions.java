package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static java.util.Objects.deepEquals;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static java.util.Objects.requireNonNull;

public final class Conditions {

    public static final PreservingCondition<Object> isNull = ConditionSupport.preserving("value is null",
            "value was not null", actual -> actual == null);
    public static final PreservingCondition<Object> isNotNull = ConditionSupport.preserving("value is not null",
            "value was null", actual -> actual != null);

    private Conditions() {}

    /** Creates a named check using the supplied callback for every execution. */
    public static <OBSERVED> PreservingCondition<OBSERVED> condition(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>> evaluation) {
        return ConditionSupport.preserving(description, evaluation);
    }

    /** Creates callbacks lazily for each wait; separate sequence stages have independent callbacks. */
    public static <OBSERVED> PreservingCondition<OBSERVED> conditionPerWait(String description,
            Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory) {
        return ConditionSupport.preserving(description, evaluatorFactory);
    }

    public static <OBSERVED> PreservingCondition<OBSERVED> asserted(CheckedConsumer<? super OBSERVED> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return ConditionSupport.preserving("value satisfies assertion", actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return unsatisfied("value did not satisfy assertion", error);
            }
        });
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> equalTo(EXPECTED expected) {
        return new ComparisonCondition<>(ConditionSupport.condition("value equals expected", expectedReference(expected), actual -> deepEquals(actual, expected)
                ? satisfied(actual) : unsatisfied("value was not equal")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notEqualTo(EXPECTED unexpected) {
        return new ComparisonCondition<>(ConditionSupport.condition("value does not equal unexpected", unexpectedReference(unexpected), actual -> !deepEquals(actual, unexpected)
                ? satisfied(actual) : unsatisfied("value was equal")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> sameAs(EXPECTED expected) {
        return new ComparisonCondition<>(ConditionSupport.condition("value is the same instance", expectedReference(expected), actual -> actual == expected
                ? satisfied(actual) : unsatisfied("value was a different instance")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notSameAs(EXPECTED unexpected) {
        return new ComparisonCondition<>(ConditionSupport.condition("value is not the same instance", unexpectedReference(unexpected), actual -> actual != unexpected
                ? satisfied(actual) : unsatisfied("value was the same instance")));
    }

    public static <RESULT> Condition.Typed<Object, RESULT> instanceOf(Class<RESULT> type) {
        requireNonNull(type, "type must not be null");
        return ConditionSupport.condition("value is an instance of " + type.getTypeName(), null, actual ->
                type.isInstance(actual)
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not an instance of " + type.getTypeName()));
    }

    public static <RESULT> Condition.Typed<Object, RESULT> exactlyInstanceOf(Class<RESULT> type) {
        requireNonNull(type, "type must not be null");
        return ConditionSupport.condition("value is exactly an instance of " + type.getTypeName(), null, actual ->
                actual != null && actual.getClass() == type
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not exactly an instance of " + type.getTypeName()));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> in(Iterable<? extends EXPECTED> expected) {
        List<?> values = snapshot(expected, "expected values");
        return new ComparisonCondition<>(ConditionSupport.condition("value is in the expected values", expectedReference(values),
                actual -> matchesAny(values, candidate -> deepEquals(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was not in the expected values")));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notIn(Iterable<? extends EXPECTED> unexpected) {
        List<?> values = snapshot(unexpected, "unexpected values");
        return new ComparisonCondition<>(ConditionSupport.condition("value is not in the unexpected values", unexpectedReference(values),
                actual -> !matchesAny(values, candidate -> deepEquals(actual, candidate))
                ? satisfied(actual) : unsatisfied("value was in the unexpected values")));
    }

    public static <OBSERVED> PreservingCondition<OBSERVED> matches(CheckedPredicate<? super OBSERVED> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return ConditionSupport.preserving("value matches", "value did not match", predicate);
    }

    public static <BOUND, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> greaterThan(BOUND bound) {
        return comparing("greater than", bound, comparison -> comparison > 0);
    }

    public static <BOUND, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> atLeast(BOUND bound) {
        return comparing("at least", bound, comparison -> comparison >= 0);
    }

    public static <BOUND, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> lessThan(BOUND bound) {
        return comparing("less than", bound, comparison -> comparison < 0);
    }

    public static <BOUND, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> atMost(BOUND bound) {
        return comparing("at most", bound, comparison -> comparison <= 0);
    }

    public static <BOUND extends Comparable<? super BOUND>, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> between(BOUND lowerBound, BOUND upperBound) {
        validateRange(lowerBound, upperBound);
        return preservingNonNull("value", "value is between the inclusive bounds", "value was outside the inclusive range",
                expectedReference(List.of(lowerBound, upperBound)),
                actual -> actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0);
    }

    public static <BOUND extends Comparable<? super BOUND>, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> strictlyBetween(BOUND lowerBound, BOUND upperBound) {
        validateRange(lowerBound, upperBound);
        return preservingNonNull("value", "value is strictly between the bounds", "value was outside the exclusive range",
                expectedReference(List.of(lowerBound, upperBound)),
                actual -> actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0);
    }

    private static <BOUND, OBSERVED extends Comparable<? super BOUND>>
            PreservingCondition<OBSERVED> comparing(String relation, BOUND bound, IntPredicate matches) {
        requireNonNull(bound, "bound must not be null");
        return preservingNonNull("value", "value is " + relation + " the bound",
                "value was not " + relation + " the bound", expectedReference(bound), actual -> matches.test(actual.compareTo(bound)));
    }

    private static <VALUE extends Comparable<? super VALUE>> void validateRange(VALUE lowerBound, VALUE upperBound) {
        requireNonNull(lowerBound, "lower bound must not be null");
        requireNonNull(upperBound, "upper bound must not be null");
        if (lowerBound.compareTo(upperBound) > 0) {
            throw new IllegalArgumentException("lower bound must not be greater than upper bound");
        }
    }

}
