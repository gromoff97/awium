package io.github.gromoff97.awium.exceptions;

public abstract class AwaitUncontrolledException extends RuntimeException {

    AwaitUncontrolledException(String message, Throwable cause) {
        super(message, cause);
    }

    public static final class AwaitSourceRetrievalException extends AwaitUncontrolledException {

        public AwaitSourceRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AwaitConditionEvaluationException extends AwaitUncontrolledException {

        public AwaitConditionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AwaitInterruptedException extends AwaitUncontrolledException {

        public AwaitInterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class AwaitUnhandledException extends AwaitUncontrolledException {

        public AwaitUnhandledException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
