package io.github.gromoff97.assertility;

import java.util.Objects;

public final class AwaitConditions {

    private AwaitConditions() {
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
