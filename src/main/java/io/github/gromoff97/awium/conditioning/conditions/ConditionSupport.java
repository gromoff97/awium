package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;

import java.util.Collection;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.condition;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.description;
import static io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime.preservingEvaluator;
import static java.util.Objects.requireNonNull;

final class ConditionSupport {

    private ConditionSupport() {
        throw new AssertionError("Utility class");
    }

    static <T> Condition<T, T> preserve(PreservingStage<? super T> nested) {
        return condition(description(nested), () -> preservingEvaluator(nested));
    }

    static <S> PreservingCondition<S> preserving(String description, String mismatch,
            Predicate<? super S> matches) {
        return ConditionRuntime.preserving(description, actual -> matches.test(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <S> PreservingCondition<S> preservingNonNull(String subject, String description,
            String mismatch, Predicate<? super S> matches) {
        return ConditionRuntime.preserving(description, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <S> PreservingCondition<S> sized(String subject, int bound, IntPredicate matches,
            String description, ToIntFunction<? super S> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        return ConditionRuntime.preserving(description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = sizeOf.applyAsInt(actual);
            return matches.test(size) ? satisfied(actual) : unsatisfied(subject + " size was " + size);
        });
    }

    static void validateRange(int lowerBound, int upperBound, String measure) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
    }

    static <T> T nonNull(T value, String name) {
        return requireNonNull(value, name + " must not be null");
    }

    static <E> E[] nonEmpty(E[] values, String name) {
        nonNull(values, name);
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    static <C extends Collection<?>> C nonEmpty(C values, String name) {
        nonNull(values, name);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    static <M extends Map<?, ?>> M nonEmpty(M values, String name) {
        nonNull(values, name);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }
}
