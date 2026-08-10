package io.github.gromoff97.awium.conditioning.conditions;

import java.util.Objects;

public final class PreservingCondition<S> {

    private final RuntimeCondition<S, S> runtime;

    private PreservingCondition(RuntimeCondition<S, S> runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public static <S> PreservingCondition<S> of(
            RuntimeCondition<S, S> runtime) {
        return new PreservingCondition<>(runtime);
    }

    public final ExplainedCondition<S> because(String explanation) {
        return new ExplainedCondition<>(this,
                RuntimeCondition.literalExplanation(explanation));
    }

    public final ExplainedCondition<S> because(
            String format, Object... arguments) {
        return new ExplainedCondition<>(this,
                RuntimeCondition.formattedExplanation(format, arguments));
    }

    public RuntimeCondition<S, S> runtime() {
        return runtime;
    }

    public static final class ExplainedCondition<S> {

        private final PreservingCondition<S> delegate;
        private final String explanation;

        private ExplainedCondition(PreservingCondition<S> delegate,
                String explanation) {
            this.delegate = Objects.requireNonNull(delegate);
            this.explanation = Objects.requireNonNull(explanation);
        }

        public PreservingCondition<S> delegate() {
            return delegate;
        }

        public String explanation() {
            return explanation;
        }
    }
}
