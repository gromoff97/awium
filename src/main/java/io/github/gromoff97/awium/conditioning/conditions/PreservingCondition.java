package io.github.gromoff97.awium.conditioning.conditions;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.literalExplanation;
import static java.util.Objects.requireNonNull;

public record PreservingCondition<S>(RuntimeCondition<S, S> runtime) {

    public PreservingCondition {
        requireNonNull(runtime, "runtime condition must not be null");
    }

    public static <S> PreservingCondition<S> of(
            RuntimeCondition<S, S> runtime) {
        return new PreservingCondition<>(runtime);
    }

    public ExplainedCondition<S> because(String explanation) {
        return new ExplainedCondition<>(this, explanation);
    }

    public ExplainedCondition<S> because(
            String format, Object... arguments) {
        return new ExplainedCondition<>(this, formattedExplanation(format, arguments));
    }

    public record ExplainedCondition<S>(PreservingCondition<S> delegate,
            String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
