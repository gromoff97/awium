package io.github.gromoff97.awium.condition;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.sources.Source;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Typed condition whose {@code because} methods return an immutable copy with a replacement explanation.
 * Condition families preserve, check, narrow, select, or transform the observed value.
 *
 * @param <Observed> value supplied to the condition
 * @param <Result> value returned when the condition is satisfied
 */
public sealed interface Condition<Observed, Result> extends AwaitCondition
        permits ConditionRuntime.RuntimeCondition {

    default Condition<Observed, Result> because(String explanation) {
        return ConditionRuntime.explained(this, explanation);
    }

    default Condition<Observed, Result> because(String format, Object... arguments) {
        return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
    }

    private static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    sealed interface PreservingCondition<Observed> extends AwaitCondition
            permits ConditionRuntime.RuntimePreservingCondition {

        default PreservingCondition<Observed> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default PreservingCondition<Observed> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    sealed interface ExpectedCondition<Expected> extends AwaitCondition
            permits ConditionRuntime.RuntimeExpectedCondition {

        default ExpectedCondition<Expected> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default ExpectedCondition<Expected> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    sealed interface NarrowingCondition<Result> extends AwaitCondition
            permits ConditionRuntime.RuntimeNarrowingCondition {

        default NarrowingCondition<Result> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default NarrowingCondition<Result> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

    sealed interface SelectedCondition<Observed, Family extends Source<?>> extends AwaitCondition
            permits ConditionRuntime.RuntimeSelectedCondition {

        default SelectedCondition<Observed, Family> because(String explanation) {
            return ConditionRuntime.explained(this, explanation);
        }

        default SelectedCondition<Observed, Family> because(String format, Object... arguments) {
            return ConditionRuntime.explained(this, formattedExplanation(format, arguments));
        }
    }

}
