package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

public sealed interface Condition<S, R> extends ResultStage<S, R> permits ConditionRuntime.RuntimeCondition {

    static <S, R> Condition<S, R> condition(String description,
            Function<? super S, Evaluation<R>> evaluation) {
        return ConditionRuntime.condition(description, evaluation);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <S> Condition<S, List<S>> caught(Predicate<? super S> first,
            Predicate<? super S> second, Predicate<? super S>... rest) {
        return ConditionRuntime.caught(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <S> Condition<S, List<S>> caught(PreservingStage<? super S> first,
            PreservingStage<? super S> second, PreservingStage<? super S>... rest) {
        return ConditionRuntime.caught(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <S, R> Condition<S, List<R>> caught(ResultStage<S, R> first,
            ResultStage<S, R> second, ResultStage<S, R>... rest) {
        return ConditionRuntime.caught(first, second, rest);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <S, F extends Source<?>> SelectedSequenceCondition<S, F> caught(SelectedStage<? super S, F> first,
            SelectedStage<? super S, F> second,
            SelectedStage<? super S, F>... rest) {
        return ConditionRuntime.caught(first, second, rest);
    }

    static <S> PreservingCondition<S> asserted(Consumer<? super S> assertion) {
        requireNonNull(assertion, "assertion must not be null");
        return ConditionRuntime.preserving("value satisfies assertion", actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return assertionUnsatisfied("value did not satisfy assertion", error);
            }
        });
    }

    static <S, R> Condition<S, R> yields(Function<? super S, ? extends R> callback) {
        requireNonNull(callback, "callback must not be null");
        return condition("callback yields a result",
                actual -> satisfied(callback.apply(actual)));
    }

    default ResultStage<S, R> because(String explanation) {
        return ConditionRuntime.explained(this, explanation);
    }

    default ResultStage<S, R> because(String format, Object... arguments) {
        return ConditionRuntime.explained(this,
                formattedExplanation(format, arguments));
    }

    private static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    public sealed interface PreservingStage<S> extends ConditionStage<S, S> permits PreservingCondition {
    }

    public sealed interface PreservingCondition<S> extends PreservingStage<S> permits ConditionRuntime.RuntimePreservingCondition {

        default PreservingStage<S> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default PreservingStage<S> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this,
                    formattedExplanation(format, arguments));
        }
    }

    public sealed interface SelectedStage<S, F extends Source<?>> extends ConditionStage<S, Object> permits SelectedCondition {
    }

    public sealed interface SelectedCondition<S, F extends Source<?>> extends SelectedStage<S, F> permits ConditionRuntime.RuntimeSelectedCondition {

        default SelectedStage<S, F> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default SelectedStage<S, F> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this,
                    formattedExplanation(format, arguments));
        }
    }

    public sealed interface SelectedSequenceStage<S, F extends Source<?>> extends ConditionStage<S, List<Object>> permits SelectedSequenceCondition {
    }

    public sealed interface SelectedSequenceCondition<S, F extends Source<?>> extends SelectedSequenceStage<S, F>
            permits ConditionRuntime.RuntimeSelectedSequenceCondition {

        default SelectedSequenceStage<S, F> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default SelectedSequenceStage<S, F> because(String format,
                Object... arguments) {
            return ConditionRuntime.explained(this,
                    formattedExplanation(format, arguments));
        }
    }
}
