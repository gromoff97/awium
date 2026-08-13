package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.literalExplanation;
import static java.util.Objects.requireNonNull;

public abstract class Condition<S, R> {

    protected Condition() {
    }

    public abstract Evaluation<R> evaluate(S actual) throws Exception;

    public abstract String description();

    public final ExplainedCondition<S, R> because(String explanation) {
        return new ExplainedCondition<>(this,
                literalExplanation(explanation));
    }

    public final ExplainedCondition<S, R> because(
            String format, Object... arguments) {
        return new ExplainedCondition<>(this,
                formattedExplanation(format, arguments));
    }

    public record ExplainedCondition<S, R>(Condition<S, R> delegate,
            String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
