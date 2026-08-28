package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.sources.Source;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Compile-time condition grammar. A {@code *Condition} still exposes {@code because}; calling it returns the
 * corresponding terminal {@code *Stage}, so a second explanation cannot compile. The stage family describes how
 * {@code until} derives its result: preserve the observed value, validate an expected type, narrow it, or select an
 * element from a structured source.
 *
 * @param <Observed> value supplied to the condition
 * @param <Result> value returned by {@code until} when the condition is satisfied
 */
public sealed interface Condition<Observed, Result> extends ResultStage<Observed, Result>
        permits ConditionRuntime.RuntimeCondition {

    default ResultStage<Observed, Result> because(String explanation) {
        return ConditionRuntime.explained(this, explanation);
    }

    default ResultStage<Observed, Result> because(String format, Object... arguments) {
        return ConditionRuntime.explained(this,
                formattedExplanation(format, arguments));
    }

    private static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    public sealed interface PreservingStage<Observed> extends ConditionStage<Observed, Observed>
            permits PreservingCondition {
    }

    public sealed interface PreservingCondition<Observed> extends PreservingStage<Observed>
            permits ConditionRuntime.RuntimePreservingCondition {

        default PreservingStage<Observed> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default PreservingStage<Observed> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this,
                    formattedExplanation(format, arguments));
        }
    }

    public sealed interface ExpectedStage<Expected> extends AwaitCondition permits ExpectedCondition {
    }

    public sealed interface ExpectedCondition<Expected> extends ExpectedStage<Expected>
            permits ConditionRuntime.RuntimeExpectedCondition {

        default ExpectedStage<Expected> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default ExpectedStage<Expected> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    public sealed interface ExpectedSequenceStage<Expected> extends AwaitCondition permits ExpectedSequenceCondition {
    }

    public sealed interface ExpectedSequenceCondition<Expected> extends ExpectedSequenceStage<Expected>
            permits ConditionRuntime.RuntimeExpectedSequenceCondition {

        default ExpectedSequenceStage<Expected> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default ExpectedSequenceStage<Expected> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    public sealed interface NarrowingStage<Result> extends AwaitCondition permits NarrowingCondition {
    }

    public sealed interface NarrowingCondition<Result> extends NarrowingStage<Result>
            permits ConditionRuntime.RuntimeNarrowingCondition {

        default NarrowingStage<Result> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default NarrowingStage<Result> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    public sealed interface SelectedStage<Observed, Family extends Source<?>> extends AwaitCondition
            permits SelectedCondition {
    }

    public sealed interface SelectedCondition<Observed, Family extends Source<?>> extends SelectedStage<Observed, Family>
            permits ConditionRuntime.RuntimeSelectedCondition {

        default SelectedStage<Observed, Family> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default SelectedStage<Observed, Family> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this,
                    formattedExplanation(format, arguments));
        }
    }

    public sealed interface SelectedSequenceStage<Observed, Family extends Source<?>> extends AwaitCondition
            permits SelectedSequenceCondition {
    }

    public sealed interface SelectedSequenceCondition<Observed, Family extends Source<?>> extends SelectedSequenceStage<Observed, Family>
            permits ConditionRuntime.RuntimeSelectedSequenceCondition {

        default SelectedSequenceStage<Observed, Family> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default SelectedSequenceStage<Observed, Family> because(String format,
                Object... arguments) {
            return ConditionRuntime.explained(this,
                    formattedExplanation(format, arguments));
        }
    }
}
