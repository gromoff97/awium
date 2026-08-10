package io.github.gromoff97.awium;

import java.util.Objects;
import java.util.Optional;

public final class Present {

    private final ConditionRuntime<Optional<?>, Object> runtime;

    Present(ConditionRuntime<Optional<?>, Object> runtime) {
        this.runtime = Objects.requireNonNull(runtime);
    }

    public final Explained because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final Explained because(String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    ConditionRuntime<Optional<?>, Object> runtime() {
        return runtime;
    }

    public static final class Explained {

        private final Present delegate;
        private final String explanation;

        Explained(Present delegate, String explanation) {
            this.delegate = Objects.requireNonNull(delegate);
            this.explanation = Objects.requireNonNull(explanation);
        }

        Present delegate() {
            return delegate;
        }

        String explanation() {
            return explanation;
        }
    }
}
