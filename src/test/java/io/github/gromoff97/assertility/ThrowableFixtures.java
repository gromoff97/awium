package io.github.gromoff97.assertility;

final class ThrowableFixtures {

    private ThrowableFixtures() {
    }

    static final class Checked extends Exception {
        private static final long serialVersionUID = 1L;

        Checked(String message) {
            super(message);
        }
    }

    static final class Fatal extends VirtualMachineError {
        private static final long serialVersionUID = 1L;

        Fatal(String message) {
            super(message);
        }
    }
}
