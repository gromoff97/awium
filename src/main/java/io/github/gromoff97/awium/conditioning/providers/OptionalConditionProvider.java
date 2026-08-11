package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;
import static java.util.Objects.requireNonNull;

public final class OptionalConditionProvider {

    public static final PresentCondition present =
            PresentCondition.of(new RuntimeCondition<>(actual -> {
            if (actual == null) {
                return unsatisfied("optional was null");
            }
            return actual.isPresent()
                    ? satisfied(actual.orElseThrow())
                    : unsatisfied("optional was empty");
        }, () -> "optional to remain present", null));

    public static final Condition<Optional<?>, Void> absent =
            ConditionProvider.condition("optional to be absent", actual -> {
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
        requireNonNull(expected, "expected must not be null");
        return valueCondition(expected, true);
    }

    public static <T> Condition<Optional<T>, T> hasValueNotEqualTo(T unexpected) {
        requireNonNull(unexpected, "unexpected must not be null");
        return valueCondition(unexpected, false);
    }

    private static <T> Condition<Optional<T>, T> valueCondition(
            T operand, boolean equal) {
        return new Condition<>() {
            @Override
            public Evaluation<T> evaluate(Optional<T> actual) {
                if (actual == null) {
                    return unsatisfied("optional was null");
                }
                if (actual.isEmpty()) {
                    return unsatisfied("optional was empty");
                }
                T value = actual.orElseThrow();
                return equal(value, operand) == equal
                        ? satisfied(value)
                        : unsatisfied(equal
                                ? "optional value was not equal"
                                : "optional value was equal");
            }

            @Override
            public String description() {
                return "optional value " + (equal ? "equal to " : "not equal to ")
                        + operand;
            }
        };
    }
}
