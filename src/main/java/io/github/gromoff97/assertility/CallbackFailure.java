package io.github.gromoff97.assertility;

final class CallbackFailure extends RuntimeException {
    private final AssertionError original;

    CallbackFailure(AssertionError original) {
        super(original);
        this.original = original;
    }

    AssertionError original() {
        return original;
    }
}
