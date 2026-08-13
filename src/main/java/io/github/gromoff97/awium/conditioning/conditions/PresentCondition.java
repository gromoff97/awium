package io.github.gromoff97.awium.conditioning.conditions;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.literalExplanation;
import static java.util.Objects.requireNonNull;

public final class PresentCondition {

    private final RuntimeCondition<Optional<?>, Object> runtime;

    private PresentCondition(RuntimeCondition<Optional<?>, Object> runtime) {
        this.runtime = requireNonNull(runtime,
                "runtime condition must not be null");
    }

    public static PresentCondition of(
            RuntimeCondition<Optional<?>, Object> runtime) {
        return new PresentCondition(runtime);
    }

    public ExplainedCondition because(String explanation) {
        return new ExplainedCondition(this,
                literalExplanation(explanation));
    }

    public ExplainedCondition because(String format, Object... arguments) {
        return new ExplainedCondition(this,
                formattedExplanation(format, arguments));
    }

    public RuntimeCondition<Optional<?>, Object> runtime() {
        return runtime;
    }

    public record ExplainedCondition(PresentCondition delegate,
            String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate, "condition must not be null");
            explanation = literalExplanation(explanation);
        }
    }
}
