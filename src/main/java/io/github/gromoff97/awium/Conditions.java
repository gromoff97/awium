package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.Condition.CapturedSource;
import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.function.CheckedConsumer;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.expectedReference;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.snapshot;
import static io.github.gromoff97.awium.ConditionSupport.unexpectedReference;
import static io.github.gromoff97.awium.ValueMatching.matchesAny;
import static java.util.Objects.deepEquals;
import static java.util.Objects.requireNonNull;

public final class Conditions {

    public static final PreservingCondition<Object> isNull = ConditionSupport.preserving("value is null",
            "value was not null", actual -> actual == null);
    public static final PreservingCondition<Object> isNotNull = ConditionSupport.preserving("value is not null",
            "value was null", actual -> actual != null);

    private Conditions() {}

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <CHECK extends Condition.Check> CapturedSource<CHECK> captured(CHECK... conditions) {
        // Keep the check type so mixed input bounds can be inferred from the terminal call.
        return new CapturedSource<>(conditions);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <INPUT, EXPECTED> CapturedSource<Condition.ApplicableCondition<INPUT, Reference<? super EXPECTED>>> captured(
            Condition.ApplicableCondition<? super INPUT, ? super Reference<? super EXPECTED>>... conditions) {
        return new CapturedSource<>(conditions);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <INPUT> CapturedSource<Condition.InputCondition<INPUT>> captured(
            Condition.InputCondition<? super INPUT>... conditions) {
        return new CapturedSource<>(conditions);
    }

    public static <INPUT, RESULT> Condition.Typed<INPUT, RESULT> condition(String description,
            CheckedFunction<? super INPUT, ? extends ConditionResult<? extends RESULT>> evaluation) {
        var condition = ConditionSupport.<INPUT, RESULT>condition(description, null,
                (INPUT actual) -> AttemptResult.of(evaluation.apply(actual)));
        requireNonNull(evaluation, "evaluation must not be null");
        return condition;
    }

    public static <INPUT, RESULT> Condition.Typed<INPUT, RESULT> conditionPerWait(String description,
            Callable<? extends CheckedFunction<? super INPUT, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory) {
        var condition = new Condition.Typed<INPUT, RESULT>(description, null, () -> {
            var evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            return actual -> AttemptResult.of(evaluator.apply(actual));
        });
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return condition;
    }

    public static <INPUT> PreservingCondition<INPUT> asserted(CheckedConsumer<? super INPUT> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return ConditionSupport.preserving("value satisfies assertion", actual -> {
            try {
                assertion.accept(actual);
                return satisfied(null);
            } catch (AssertionError error) {
                return unsatisfied("value did not satisfy assertion", error);
            }
        });
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> equalTo(EXPECTED expected) {
        return comparison("value equals expected", "value was not equal", expectedReference(expected),
                actual -> deepEquals(actual, expected));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notEqualTo(EXPECTED unexpected) {
        return comparison("value does not equal unexpected", "value was equal", unexpectedReference(unexpected),
                actual -> !deepEquals(actual, unexpected));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> sameAs(EXPECTED expected) {
        return comparison("value is the same instance", "value was a different instance", expectedReference(expected),
                actual -> actual == expected);
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notSameAs(EXPECTED unexpected) {
        return comparison("value is not the same instance", "value was the same instance", unexpectedReference(unexpected),
                actual -> actual != unexpected);
    }

    public static <RESULT> Condition.Typed<Object, RESULT> instanceOf(Class<RESULT> type) {
        requireNonNull(type, "type must not be null");
        return ConditionSupport.condition("value is an instance of " + type.getTypeName(), null, actual ->
                type.isInstance(actual)
                        ? AttemptResult.satisfied(type.cast(actual))
                        : AttemptResult.unsatisfied(typeMismatch(actual)));
    }

    public static <RESULT> Condition.Typed<Object, RESULT> exactlyInstanceOf(Class<RESULT> type) {
        requireNonNull(type, "type must not be null");
        return ConditionSupport.condition("value is exactly an instance of " + type.getTypeName(), null, actual ->
                actual != null && actual.getClass() == type
                        ? AttemptResult.satisfied(type.cast(actual))
                        : AttemptResult.unsatisfied(typeMismatch(actual)));
    }

    private static String typeMismatch(Object actual) {
        return actual == null ? "value was null" : "value type was " + actual.getClass().getTypeName();
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> in(Iterable<? extends EXPECTED> expected) {
        List<?> values = snapshot(expected, "expected values");
        return comparison("value is in the expected values", "value was not in the expected values", expectedReference(values),
                actual -> matchesAny(values, candidate -> deepEquals(actual, candidate)));
    }

    public static <EXPECTED> ComparisonCondition<EXPECTED> notIn(Iterable<? extends EXPECTED> unexpected) {
        List<?> values = snapshot(unexpected, "unexpected values");
        return comparison("value is not in the unexpected values", "value was in the unexpected values", unexpectedReference(values),
                actual -> !matchesAny(values, candidate -> deepEquals(actual, candidate)));
    }

    public static <INPUT> PreservingCondition<INPUT> matches(CheckedPredicate<? super INPUT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return ConditionSupport.preserving("value matches", "value did not match", predicate);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> greaterThan(BOUND bound) {
        return comparing("greater than", bound, comparison -> comparison > 0);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> atLeast(BOUND bound) {
        return comparing("at least", bound, comparison -> comparison >= 0);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> lessThan(BOUND bound) {
        return comparing("less than", bound, comparison -> comparison < 0);
    }

    public static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> atMost(BOUND bound) {
        return comparing("at most", bound, comparison -> comparison <= 0);
    }

    public static <BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> between(BOUND lowerBound, BOUND upperBound) {
        validateRange(lowerBound, upperBound);
        return preservingNonNull("value", "value is between the inclusive bounds", "value was outside the inclusive range",
                expectedReference(List.of(lowerBound, upperBound)),
                actual -> actual.compareTo(lowerBound) >= 0 && actual.compareTo(upperBound) <= 0);
    }

    public static <BOUND extends Comparable<? super BOUND>, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> strictlyBetween(BOUND lowerBound, BOUND upperBound) {
        validateRange(lowerBound, upperBound);
        return preservingNonNull("value", "value is strictly between the bounds", "value was outside the exclusive range",
                expectedReference(List.of(lowerBound, upperBound)),
                actual -> actual.compareTo(lowerBound) > 0 && actual.compareTo(upperBound) < 0);
    }

    private static <EXPECTED> ComparisonCondition<EXPECTED> comparison(String description, String mismatch,
            Reference<?> reference, CheckedPredicate<Object> matches) {
        return new ComparisonCondition<>(ConditionSupport.condition(description, reference,
                actual -> matches.test(actual) ? AttemptResult.satisfied(actual) : AttemptResult.unsatisfied(mismatch)));
    }

    private static <BOUND, INPUT extends Comparable<? super BOUND>>
            PreservingCondition<INPUT> comparing(String relation, BOUND bound, IntPredicate matches) {
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
