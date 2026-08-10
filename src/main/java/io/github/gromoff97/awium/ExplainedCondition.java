package io.github.gromoff97.awium;

import java.util.Objects;

public final class ExplainedCondition<S, R> {

    private final Condition<S, R> delegate;
    private final String explanation;

    ExplainedCondition(Condition<S, R> delegate, String explanation) {
        this.delegate = Objects.requireNonNull(delegate);
        this.explanation = Objects.requireNonNull(explanation);
    }

    Condition<S, R> delegate() {
        return delegate;
    }

    String explanation() {
        return explanation;
    }
}
