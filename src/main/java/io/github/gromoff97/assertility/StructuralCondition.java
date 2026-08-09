package io.github.gromoff97.assertility;

import java.util.Objects;

public final class StructuralCondition {

    private final ConditionRuntime<Object, Object> runtime;

    StructuralCondition(ConditionRuntime<Object, Object> runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public final ExplainedStructuralCondition because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final ExplainedStructuralCondition because(
            String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    ConditionRuntime<Object, Object> runtime() {
        return runtime;
    }
}
