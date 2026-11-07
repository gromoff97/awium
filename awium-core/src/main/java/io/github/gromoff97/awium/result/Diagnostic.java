package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.Condition.Reference;
import java.util.List;

public record Diagnostic(String mismatch, List<Reference<?>> fields) {

    public static final Diagnostic EMPTY = new Diagnostic(null, List.of());

    public Diagnostic {
        fields = List.copyOf(fields);
    }

    public Diagnostic(String mismatch, Reference<?>... fields) {
        this(mismatch, List.of(fields));
    }

    public Diagnostic withMismatch(String message) {
        return new Diagnostic(message, fields);
    }

    void requireMismatch() {
        if (mismatch == null || mismatch.isBlank()) {
            throw new IllegalArgumentException("unsatisfied message must not be null or blank");
        }
    }
}
