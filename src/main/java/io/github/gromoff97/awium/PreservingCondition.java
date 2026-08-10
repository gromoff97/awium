package io.github.gromoff97.awium;

import java.util.Objects;

public final class PreservingCondition<S> {

    private final ConditionRuntime<S, S> runtime;

    PreservingCondition(ConditionRuntime<S, S> runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public final Explained<S> because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final Explained<S> because(
            String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    ConditionRuntime<S, S> runtime() {
        return runtime;
    }

    public static final class Explained<S> {

        private final PreservingCondition<S> delegate;
        private final String explanation;

        Explained(PreservingCondition<S> delegate, String explanation) {
            this.delegate = Objects.requireNonNull(delegate);
            this.explanation = Objects.requireNonNull(explanation);
        }

        PreservingCondition<S> delegate() {
            return delegate;
        }

        String explanation() {
            return explanation;
        }
    }
}
