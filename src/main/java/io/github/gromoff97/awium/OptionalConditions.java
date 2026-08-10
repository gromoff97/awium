package io.github.gromoff97.awium;

import java.util.Objects;
import java.util.Optional;

final class OptionalConditions {

    private OptionalConditions() {
    }

    static Present present() {
        return new Present(new ConditionRuntime<>(actual -> {
            if (actual == null) {
                return Evaluation.unsatisfied("optional was null");
            }
            return actual.isPresent()
                    ? Evaluation.satisfied(actual.orElseThrow())
                    : Evaluation.unsatisfied("optional was empty");
        }, () -> "optional to remain present", null));
    }

    static Condition<Optional<?>, Void> absent() {
        return new Condition<>() {
            @Override
            public Evaluation<Void> evaluate(Optional<?> actual) {
                if (actual == null) {
                    return Evaluation.unsatisfied("optional was null");
                }
                return actual.isEmpty()
                        ? Evaluation.satisfied(null)
                        : Evaluation.unsatisfied("optional was present");
            }

            @Override
            public String description() {
                return "optional to be absent";
            }
        };
    }

    static <T> Condition<Optional<T>, T> hasValueEqualTo(T expected) {
        Objects.requireNonNull(expected, "expected must not be null");
        return valueCondition(expected, true);
    }

    static <T> Condition<Optional<T>, T> hasValueNotEqualTo(T unexpected) {
        Objects.requireNonNull(unexpected, "unexpected must not be null");
        return valueCondition(unexpected, false);
    }

    private static <T> Condition<Optional<T>, T> valueCondition(
            T operand, boolean equal) {
        return new Condition<>() {
            @Override
            public Evaluation<T> evaluate(Optional<T> actual) {
                if (actual == null) {
                    return Evaluation.unsatisfied("optional was null");
                }
                if (actual.isEmpty()) {
                    return Evaluation.unsatisfied("optional was empty");
                }
                T value = actual.orElseThrow();
                return ValueEquality.equal(value, operand) == equal
                        ? Evaluation.satisfied(value)
                        : Evaluation.unsatisfied(equal
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
