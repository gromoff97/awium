package io.github.gromoff97.assertility;

import java.util.Objects;

public final class PreservingCondition<S> {

    private final ConditionRuntime<S, S> runtime;

    PreservingCondition(ConditionRuntime<S, S> runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public final ExplainedPreservingCondition<S> because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final ExplainedPreservingCondition<S> because(
            String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    ConditionRuntime<S, S> runtime() {
        return runtime;
    }
}
