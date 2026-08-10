package io.github.gromoff97.awium;

import java.util.Objects;

public final class ExplainedPresent {

    private final Present delegate;
    private final String explanation;

    ExplainedPresent(Present delegate, String explanation) {
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
