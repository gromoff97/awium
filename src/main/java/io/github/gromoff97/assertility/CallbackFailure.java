package io.github.gromoff97.assertility;

import java.io.Serial;

final class CallbackFailure extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final AssertionError original;

    CallbackFailure(AssertionError original) {
        super(original);
        this.original = original;
    }

    AssertionError original() {
        return original;
    }
}
