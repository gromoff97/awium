package io.github.gromoff97.assertility;

import java.util.Objects;

public final class ExplainedStructuralCondition {

    private final StructuralCondition delegate;
    private final String explanation;

    ExplainedStructuralCondition(StructuralCondition delegate, String explanation) {
        this.delegate = Objects.requireNonNull(delegate);
        this.explanation = Objects.requireNonNull(explanation);
    }

    StructuralCondition delegate() {
        return delegate;
    }

    String explanation() {
        return explanation;
    }
}
