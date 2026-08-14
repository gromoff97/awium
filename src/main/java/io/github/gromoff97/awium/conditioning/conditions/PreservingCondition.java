package io.github.gromoff97.awium.conditioning.conditions;

import static io.github.gromoff97.awium.conditioning.conditions.Condition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.literalExplanation;
import static java.util.Objects.requireNonNull;

public record PreservingCondition<S>(Condition<S, S> delegate) {

    public PreservingCondition {
        requireNonNull(delegate, "condition must not be null");
    }

    public ExplainedCondition<S> because(String explanation) {
        return new ExplainedCondition<>(this, explanation);
    }

    public ExplainedCondition<S> because(String format, Object... arguments) {
        return new ExplainedCondition<>(this, formattedExplanation(format, arguments));
    }

    public record ExplainedCondition<S>(PreservingCondition<S> delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
