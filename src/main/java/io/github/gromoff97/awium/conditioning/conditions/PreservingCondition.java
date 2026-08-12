package io.github.gromoff97.awium.conditioning.conditions;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.literalExplanation;
import static java.util.Objects.requireNonNull;

public final class PreservingCondition<S> {

    private final RuntimeCondition<S, S> runtime;

    private PreservingCondition(RuntimeCondition<S, S> runtime) {
        this.runtime = requireNonNull(runtime);
    }

    public static <S> PreservingCondition<S> of(
            RuntimeCondition<S, S> runtime) {
        return new PreservingCondition<>(runtime);
    }

    public ExplainedCondition<S> because(String explanation) {
        return new ExplainedCondition<>(this,
                literalExplanation(explanation));
    }

    public ExplainedCondition<S> because(
            String format, Object... arguments) {
        return new ExplainedCondition<>(this,
                formattedExplanation(format, arguments));
    }

    public RuntimeCondition<S, S> runtime() {
        return runtime;
    }

    public record ExplainedCondition<S>(PreservingCondition<S> delegate,
            String explanation) {

        public ExplainedCondition {
            requireNonNull(delegate);
            explanation = literalExplanation(explanation);
        }
    }
}
