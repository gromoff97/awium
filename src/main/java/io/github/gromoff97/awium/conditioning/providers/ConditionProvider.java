package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.CheckedConsumer;
import io.github.gromoff97.awium.conditioning.CheckedFunction;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;

import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class ConditionProvider {

    private ConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> Condition<S, R> condition(String description, CheckedFunction<? super S, Evaluation<R>> evaluation) {
        if (requireNonNull(description, "description must not be null").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        return condition(() -> description, evaluation);
    }

    static <S, R> Condition<S, R> condition(Supplier<String> description, CheckedFunction<? super S, Evaluation<R>> evaluation) {
        requireNonNull(description, "description must not be null");
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>() {
            @Override
            public Evaluation<R> evaluate(S actual) throws Exception {
                return evaluation.apply(actual);
            }

            @Override
            public String description() {
                return description.get();
            }
        };
    }

    static <S> PreservingCondition<S> preservingCondition(Supplier<String> description, CheckedFunction<? super S, Evaluation<S>> evaluation) {
        return new PreservingCondition<>(condition(description, evaluation));
    }

    static <S> PreservingCondition<S> matchingCondition(String subject, String description, String mismatch, boolean positive, Predicate<? super S> matches) {
        return preservingCondition(() -> description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            return matches.test(actual) == positive ? satisfied(actual) : unsatisfied(mismatch);
        });
    }

    public static <S> PreservingCondition<S> asserted(CheckedConsumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return new PreservingCondition<>(evaluated("value satisfies assertion",
                "value did not satisfy assertion", actual -> {
            assertion.accept(actual);
            return actual;
        }));
    }

    public static <S, R> Condition<S, R> yields(CheckedFunction<? super S, ? extends R> callback) {
        requireNonNull(callback, "callback must not be null");
        return evaluated("callback yields a result", "callback did not yield a result", callback);
    }

    private static <S, R> Condition<S, R> evaluated(String description, String mismatch,
            CheckedFunction<? super S, ? extends R> function) {
        return condition(description, actual -> {
            try {
                return satisfied(function.apply(actual));
            } catch (AssertionError error) {
                return assertionUnsatisfied(mismatch, error);
            }
        });
    }

}
