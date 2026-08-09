package io.github.gromoff97.assertility;

import java.util.Objects;

public final class ExplainedPreservingCondition<S> {

    private final PreservingCondition<S> delegate;
    private final String explanation;

    ExplainedPreservingCondition(PreservingCondition<S> delegate, String explanation) {
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
