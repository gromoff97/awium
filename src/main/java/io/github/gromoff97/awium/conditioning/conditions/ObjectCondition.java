package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.CheckedPredicate;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.copy;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.preserve;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public final class ObjectCondition {

    public static final Condition<Object, Void> isNull = condition("value is null",
            actual -> actual == null ? satisfied(null) : unsatisfied("value was not null"));
    public static final PreservingCondition<Object> isNotNull = preserving("value is not null",
            "value was null", actual -> actual != null, false);

    private ObjectCondition() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return preserving("value equals expected", "value was not equal",
                actual -> equal(actual, expected), true);
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return preserving("value does not equal unexpected", "value was equal",
                actual -> !equal(actual, unexpected), true);
    }

    public static PreservingCondition<Object> sameAs(Object expected) {
        return preserving("value is the same instance", "value was a different instance",
                actual -> actual == expected, true);
    }

    public static PreservingCondition<Object> notSameAs(Object unexpected) {
        return preserving("value is not the same instance", "value was the same instance",
                actual -> actual != unexpected, true);
    }

    public static <R> Condition<Object, R> instanceOf(Class<R> type) {
        return typed(type, false);
    }

    public static <R> Condition<Object, R> exactInstanceOf(Class<R> type) {
        return typed(type, true);
    }

    public static PreservingCondition<Object> in(Object... expected) {
        Object[] values = nonEmpty(expected, "expected values");
        return preserving("value is in the expected values", "value was not in the expected values",
                actual -> matchesAny(asList(values), candidate -> equal(actual, candidate)), true);
    }

    public static PreservingCondition<Object> notIn(Object... unexpected) {
        Object[] values = nonEmpty(unexpected, "unexpected values");
        return preserving("value is not in the unexpected values", "value was in the unexpected values",
                actual -> !matchesAny(asList(values), candidate -> equal(actual, candidate)), true);
    }

    public static <S> PreservingCondition<S> matches(CheckedPredicate<? super S> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("value matches", "value did not match", predicate, true);
    }

    public static <S, T, R> Condition<S, R> extracting(CheckedFunction<? super S, ? extends T> extractor,
            Condition<? super T, ? extends R> nested) {
        requireNonNull(extractor, "extractor must not be null");
        requireNonNull(nested, "condition must not be null");
        return condition("extracted " + nested.description(), actual -> copy(nested.evaluate(extractor.apply(actual))));
    }

    public static <S, T> Condition<S, T> extracting(CheckedFunction<? super S, ? extends T> extractor,
            PreservingCondition<? super T> nested) {
        requireNonNull(nested, "condition must not be null");
        Condition<? super T, ?> delegate = nested.delegate();
        return extracting(extractor, preserve(delegate));
    }

    private static <R> Condition<Object, R> typed(Class<R> type, boolean exact) {
        requireNonNull(type, "type must not be null");
        String qualifier = exact ? "exactly " : "";
        return condition("value is " + qualifier + "an instance of " + type.getTypeName(), actual -> {
            boolean matches = actual != null && (exact ? actual.getClass() == type : type.isInstance(actual));
            return matches ? satisfied(type.cast(actual))
                    : unsatisfied("value was not " + qualifier + "an instance of " + type.getTypeName());
        });
    }

    private static <S> PreservingCondition<S> preserving(String description, String mismatch,
            CheckedPredicate<? super S> matches, boolean allowNull) {
        return new PreservingCondition<>(condition(description, actual -> {
            if (actual == null && !allowNull) {
                return unsatisfied(mismatch);
            }
            return matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch);
        }));
    }

    private static Object[] nonEmpty(Object[] values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }
}
