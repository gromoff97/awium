package io.github.gromoff97.awium.exceptions;

public abstract class AwaitFailure extends AssertionError {

    AwaitFailure(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class AwaitTimeoutException extends AwaitFailure {

        public AwaitTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AwaitPersistenceException extends AwaitFailure {

        public AwaitPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
