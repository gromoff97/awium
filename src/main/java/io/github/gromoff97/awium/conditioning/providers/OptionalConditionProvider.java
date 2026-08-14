package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.equal;
import static java.util.Objects.requireNonNull;

public final class OptionalConditionProvider {

    public static final PresentCondition present = new PresentCondition(condition(() -> "optional is present", actual -> {
            if (actual == null) {
                return unsatisfied("optional was null");
            }
            return actual.isPresent()
                    ? satisfied(actual.orElseThrow())
                    : unsatisfied("optional was empty");
        }));

    public static final Condition<Optional<?>, Void> absent =
            condition("optional is absent", actual -> {
                if (actual == null) {
                    return unsatisfied("optional was null");
                }
                return actual.isEmpty()
                        ? satisfied(null)
                        : unsatisfied("optional was present");
            });

    private OptionalConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <T> Condition<Optional<T>, T> hasValueEqualTo(T expected) {
        return valueCondition(requireNonNull(expected, "expected must not be null"), true);
    }

    public static <T> Condition<Optional<T>, T> hasValueNotEqualTo(T unexpected) {
        return valueCondition(requireNonNull(unexpected, "unexpected must not be null"), false);
    }

    private static <T> Condition<Optional<T>, T> valueCondition(T operand, boolean equal) {
        return condition(() -> "optional value "
                        + (equal ? "equals " : "does not equal ") + operand,
                actual -> {
                    if (actual == null || actual.isEmpty()) {
                        return unsatisfied("optional was " + (actual == null ? "null" : "empty"));
                    }
                    T value = actual.orElseThrow();
                    return equal(value, operand) == equal
                            ? satisfied(value)
                            : unsatisfied(equal
                                    ? "optional value was not equal"
                                    : "optional value was equal");
                });
    }
}
