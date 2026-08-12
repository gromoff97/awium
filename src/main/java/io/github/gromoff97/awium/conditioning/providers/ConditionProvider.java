package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.CheckedConsumer;
import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

public final class ConditionProvider {

    private ConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> Condition<S, R> condition(
            String description,
            CheckedFunction<? super S, Evaluation<R>> evaluation) {
        if (requireNonNull(description, "description must not be null").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return description;
            }
        };
    }

    public static <S> PreservingCondition<S> asserted(
            CheckedConsumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return PreservingCondition.of(RuntimeCondition.open(
                ConditionProvider.<S, S>passed(actual -> {
                    assertion.accept(actual);
                    return actual;
                })));
    }

    public static <S, R> Condition<S, R> passed(
            CheckedFunction<? super S, ? extends R> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return condition("assertion to pass", actual -> {
            try {
                return satisfied(assertion.apply(actual));
            } catch (AssertionError error) {
                return assertionUnsatisfied(
                        "assertion did not pass", error);
            }
        });
    }
}
