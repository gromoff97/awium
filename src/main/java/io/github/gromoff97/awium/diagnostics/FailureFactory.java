package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.await.AwaitResult;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitPersistenceException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;

import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public final class FailureFactory {

    private FailureFactory() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> R complete(WaitOutcome<S, R> outcome,
            String description, String explanation,
            WaitConfiguration configuration) {
        if (outcome instanceof WaitOutcome.Satisfied<S, R> success) {
            return satisfied(success.attempt()).result();
        }
        Throwable failure = failure(outcome, description, explanation, configuration);
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw (Error) failure;
    }

    public static <S, R> AwaitResult<S, R> capture(WaitEngine.Execution<S, R> execution,
            String description, String explanation,
            WaitConfiguration configuration) {
        if (execution.outcome() instanceof WaitOutcome.Satisfied<S, R> success) {
            return new AwaitResult.Satisfied<>(execution.attempts(), execution.outcome().attempt().number(),
                    satisfied(success.attempt()).result());
        }
        return new AwaitResult.Failed<>(execution.attempts(), execution.outcome().attempt().number(),
                failure(execution.outcome(), description, explanation, configuration));
    }

    private static <S, R> Throwable failure(WaitOutcome<S, R> outcome,
            String description, String explanation,
            WaitConfiguration configuration) {
        Throwable cause = FailureMessageRenderer.failure(outcome.attempt());
        if (cause instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        FailureMessageRenderer.Result rendered;
        try {
            rendered = FailureMessageRenderer.render(outcome, description, explanation, configuration, cause);
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }

        Throwable renderingFailure = rendered.failure();
        if (renderingFailure != null) {
            var failure = new AwaitUnhandledException(rendered.message(), renderingFailure);
            if (cause != renderingFailure) {
                addSuppressed(failure, cause);
            }
            return failure;
        }

        String message = rendered.message();
        if (outcome instanceof WaitOutcome.PersistenceFailure<S, R>) {
            return new AwaitPersistenceException(message, cause);
        }
        if (outcome instanceof WaitOutcome.Uncontrolled<S, R>) {
            if (cause instanceof InterruptedException) {
                return new AwaitInterruptedException(message, cause);
            }
            return switch (outcome.attempt().outcome()) {
                case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored ->
                        new AwaitUnhandledException(message, cause);
                case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> ignored ->
                        new AwaitSourceRetrievalException(message, cause);
                case AwaitAttempt.Outcome.SourceInterrupted<?, ?> ignored ->
                        new AwaitSourceRetrievalException(message, cause);
                case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> ignored ->
                        new AwaitConditionEvaluationException(message, cause);
                default -> throw new IllegalArgumentException("attempt is not uncontrolled");
            };
        }
        return new AwaitTimeoutException(message, cause);
    }

    @SuppressWarnings("unchecked")
    private static <S, R> AwaitAttempt.Outcome.Satisfied<S, R> satisfied(AwaitAttempt<S, R> attempt) {
        return (AwaitAttempt.Outcome.Satisfied<S, R>) attempt.outcome();
    }

    private static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }
}
