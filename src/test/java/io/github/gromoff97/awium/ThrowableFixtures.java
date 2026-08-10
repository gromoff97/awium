package io.github.gromoff97.awium;

final class ThrowableFixtures {

    private ThrowableFixtures() {
        throw new AssertionError("Utility class");
    }

    static final class Checked extends Exception {
        Checked(String message) {
            super(message);
        }
    }

    static final class Fatal extends VirtualMachineError {
        Fatal(String message) {
            super(message);
        }
    }
}
