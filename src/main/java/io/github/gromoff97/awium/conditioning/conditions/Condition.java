package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.sources.Source;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

public sealed interface Condition<S, R> extends ResultStage<S, R> permits ConditionRuntime.RuntimeCondition {

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

    public sealed interface ExpectedStage<T> extends AwaitCondition permits ExpectedCondition {
    }

    public sealed interface ExpectedCondition<T> extends ExpectedStage<T> permits ConditionRuntime.RuntimeExpectedCondition {

        default ExpectedStage<T> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default ExpectedStage<T> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    public sealed interface SelectedStage<S, F extends Source<?>> extends AwaitCondition permits SelectedCondition {
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

    public sealed interface SelectedSequenceStage<S, F extends Source<?>> extends AwaitCondition permits SelectedSequenceCondition {
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
