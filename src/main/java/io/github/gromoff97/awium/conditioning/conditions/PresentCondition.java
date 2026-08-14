package io.github.gromoff97.awium.conditioning.conditions;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.conditions.Condition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.literalExplanation;
import static java.util.Objects.requireNonNull;

public record PresentCondition(Condition<Optional<?>, Object> delegate) {

    public PresentCondition {
        requireNonNull(delegate, "condition must not be null");
    }

    public ExplainedCondition because(String explanation) {
        return new ExplainedCondition(this, explanation);
    }

    public ExplainedCondition because(String format, Object... arguments) {
        return new ExplainedCondition(this, formattedExplanation(format, arguments));
    }

    public record ExplainedCondition(PresentCondition delegate, String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
