package io.github.gromoff97.assertility;

import java.util.Objects;
import java.util.Optional;

public final class AwaitConditions {

    public static final Present present = OptionalConditions.present();
    public static final StructuralCondition empty = StructuralConditions.empty();
    public static final StructuralCondition nonEmpty =
            StructuralConditions.nonEmpty();
    public static final Condition<Optional<?>, Void> absent =
            OptionalConditions.absent();
    public static final Condition<Object, Void> isNull = ObjectConditions.isNull();
    public static final PreservingCondition<Object> isNotNull =
            ObjectConditions.isNotNull();

    private AwaitConditions() {
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return ObjectConditions.equalTo(expected);
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return ObjectConditions.notEqualTo(unexpected);
    }

    public static StructuralCondition sizeExactly(int expected) {
        return StructuralConditions.sizeExactly(expected);
    }

    public static StructuralCondition sizeNotExactly(int unexpected) {
        return StructuralConditions.sizeNotExactly(unexpected);
    }

    public static StructuralCondition sizeGreaterThan(int lowerBound) {
        return StructuralConditions.sizeGreaterThan(lowerBound);
    }

    public static StructuralCondition sizeAtLeast(int lowerBound) {
        return StructuralConditions.sizeAtLeast(lowerBound);
    }

    public static StructuralCondition sizeLessThan(int upperBound) {
        return StructuralConditions.sizeLessThan(upperBound);
    }

    public static StructuralCondition sizeAtMost(int upperBound) {
        return StructuralConditions.sizeAtMost(upperBound);
    }

    public static <T> Condition<Optional<T>, T> hasValueEqualTo(T expected) {
        return OptionalConditions.hasValueEqualTo(expected);
    }

    public static <T> Condition<Optional<T>, T> hasValueNotEqualTo(T unexpected) {
        return OptionalConditions.hasValueNotEqualTo(unexpected);
    }

    public static <S, R> Condition<S, R> condition(
            String description,
            ThrowingFunction<? super S, Evaluation<R>> evaluation) {
        String checked = Validation.nonBlank(description, "description");
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return checked;
            }
        };
    }

    public static <S> PreservingCondition<S> asserted(
            ThrowingConsumer<? super S> assertion) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        return new PreservingCondition<>(new ConditionRuntime<>(actual -> {
            try {
                assertion.accept(actual);
                return Evaluation.satisfied(actual);
            } catch (AssertionError error) {
                return Evaluation.assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        }, () -> "assertion to pass", null));
    }

    public static <S, R> Condition<S, R> passed(
            ThrowingFunction<? super S, ? extends R> assertion) {
        Objects.requireNonNull(assertion, "assertion must not be null");
        return condition("assertion to pass", actual -> {
            try {
                R result = assertion.apply(actual);
                return Evaluation.satisfied(result);
            } catch (AssertionError error) {
                return Evaluation.assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        });
    }
}
