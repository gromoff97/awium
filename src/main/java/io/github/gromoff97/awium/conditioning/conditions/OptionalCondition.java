package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.CheckedPredicate;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PresentCondition;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.copy;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.failure;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionResults.preserve;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static java.util.Objects.requireNonNull;

public final class OptionalCondition {

    public static final PresentCondition present = new PresentCondition(condition("optional is present", actual -> {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }));
    public static final Condition<Optional<?>, Void> absent = condition("optional is absent", actual -> {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isEmpty() ? satisfied(null) : unsatisfied("optional was present");
    });

    private OptionalCondition() {
        throw new AssertionError("Utility class");
    }

    public static <T> Condition<Optional<T>, T> hasValue(T expected) {
        requireNonNull(expected, "expected must not be null");
        return selected("optional value equals expected", "optional value was not equal",
                actual -> equal(actual, expected));
    }

    public static <T> Condition<Optional<T>, T> doesNotHaveValue(T unexpected) {
        requireNonNull(unexpected, "unexpected must not be null");
        return selected("optional value does not equal unexpected", "optional value was equal",
                actual -> !equal(actual, unexpected));
    }

    public static <T> Condition<Optional<T>, T> hasValue(CheckedPredicate<? super T> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("optional value matches", "optional value did not match", predicate);
    }

    public static <R> Condition<Optional<?>, R> hasValue(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("optional contains an instance of " + type.getTypeName(), actual -> {
            Evaluation<?> selected = present(actual);
            if (selected.status() != Evaluation.Status.SATISFIED) {
                return failure(selected);
            }
            Object value = selected.result();
            return type.isInstance(value) ? satisfied(type.cast(value))
                    : unsatisfied("optional value had a different type");
        });
    }

    public static <T, R> Condition<Optional<T>, R> hasValue(Condition<? super T, ? extends R> nested) {
        requireNonNull(nested, "condition must not be null");
        return condition("optional value " + nested.description(), actual -> {
            Evaluation<T> selected = present(actual);
            return selected.status() == Evaluation.Status.SATISFIED
                    ? copy(nested.evaluate(selected.result())) : failure(selected);
        });
    }

    public static <T> Condition<Optional<T>, T> hasValue(PreservingCondition<? super T> nested) {
        requireNonNull(nested, "condition must not be null");
        return hasValue(preserve(nested.delegate()));
    }

    private static <T> Condition<Optional<T>, T> selected(String description, String mismatch,
            CheckedPredicate<? super T> predicate) {
        return condition(description, actual -> {
            Evaluation<T> selected = present(actual);
            if (selected.status() != Evaluation.Status.SATISFIED) {
                return failure(selected);
            }
            T value = selected.result();
            return predicate.test(value) ? satisfied(value) : unsatisfied(mismatch);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> Evaluation<T> present(Optional<T> actual) throws Exception {
        return (Evaluation<T>) present.delegate().evaluate(actual);
    }

}
