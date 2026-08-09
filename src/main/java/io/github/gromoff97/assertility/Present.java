package io.github.gromoff97.assertility;

import java.util.Objects;
import java.util.Optional;

public final class Present {

    private final ConditionRuntime<Optional<?>, Object> runtime;

    Present(ConditionRuntime<Optional<?>, Object> runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public final ExplainedPresent because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final ExplainedPresent because(String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    ConditionRuntime<Optional<?>, Object> runtime() {
        return runtime;
    }
}
