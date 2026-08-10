package io.github.gromoff97.awium.conditioning.conditions;

import java.util.Optional;

import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.formattedExplanation;
import static io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition.literalExplanation;
import static java.util.Objects.requireNonNull;

public final class PresentCondition {

    private final RuntimeCondition<Optional<?>, Object> runtime;

    private PresentCondition(RuntimeCondition<Optional<?>, Object> runtime) {
        this.runtime = requireNonNull(runtime);
    }

    public static PresentCondition of(
            RuntimeCondition<Optional<?>, Object> runtime) {
        return new PresentCondition(runtime);
    }

    public final ExplainedCondition because(String explanation) {
        return new ExplainedCondition(this,
                literalExplanation(explanation));
    }

    public final ExplainedCondition because(String format, Object... arguments) {
        return new ExplainedCondition(this,
                formattedExplanation(format, arguments));
    }

    public RuntimeCondition<Optional<?>, Object> runtime() {
        return runtime;
    }

    public static final class ExplainedCondition {

        private final PresentCondition delegate;
        private final String explanation;

        private ExplainedCondition(PresentCondition delegate,
                String explanation) {
            this.delegate = requireNonNull(delegate);
            this.explanation = requireNonNull(explanation);
        }

        public PresentCondition delegate() {
            return delegate;
        }

        public String explanation() {
            return explanation;
        }
    }
}
