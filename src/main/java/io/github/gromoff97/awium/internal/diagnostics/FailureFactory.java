package io.github.gromoff97.awium.internal.diagnostics;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.results.AwaitAttempt.Reference;
import io.github.gromoff97.awium.internal.engine.WaitCompletion;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.internal.engine.WaitEngine;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitPersistenceException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;

import static io.github.gromoff97.awium.internal.diagnostics.FailureMessageRenderer.addSuppressed;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public final class FailureFactory {

    private FailureFactory() {
        throw new AssertionError("Utility class");
    }

    public static <Observed, Result> Result complete(WaitCompletion<Observed, Result> outcome,
            String description, String explanation, Reference<?> reference,
            WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<Observed, Result> success) {
            return satisfied(success.attempt()).result();
        }
        Throwable failure = failure(outcome, description, explanation, reference, configuration);
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw (Error) failure;
    }

    public static <Observed, Result> AwaitResult<Observed, Result> capture(WaitEngine.RecordedWait<Observed, Result> execution,
            String description, String explanation, Reference<?> reference,
            WaitConfiguration configuration) {
        if (execution.outcome() instanceof WaitCompletion.Satisfied<Observed, Result> success) {
            return new AwaitResult.Satisfied<>(execution.attempts(), success.attempt().number(),
                    satisfied(success.attempt()).result());
        }
        return new AwaitResult.Failed<>(execution.attempts(), execution.outcome().attempt().number(),
                failure(execution.outcome(), description, explanation, reference, configuration));
    }

    private static <Observed, Result> Throwable failure(WaitCompletion<Observed, Result> outcome,
            String description, String explanation, Reference<?> reference,
            WaitConfiguration configuration) {
        FailureMessageRenderer.AttemptDiagnostic diagnostic = FailureMessageRenderer.diagnostic(outcome.attempt());
        Throwable cause = diagnostic.failure();
        if (cause instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        FailureMessageRenderer.Result rendered;
        try {
            rendered = FailureMessageRenderer.render(outcome, description, explanation, reference,
                    configuration, diagnostic);
            restoreInterrupt |= rendered.failure() instanceof InterruptedException;
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
        if (outcome instanceof WaitCompletion.PersistenceFailure<Observed, Result>) {
            return new AwaitPersistenceException(message, cause);
        }
        if (outcome instanceof WaitCompletion.Uncontrolled<Observed, Result>) {
            if (cause instanceof InterruptedException) {
                return new AwaitInterruptedException(message, cause);
            }
            return switch (outcome.attempt().outcome()) {
                case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored ->
                        new AwaitUnhandledException(message, cause);
                case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> ignored ->
                        new AwaitSourceRetrievalException(message, cause);
                case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> ignored ->
                        new AwaitConditionEvaluationException(message, cause);
                default -> throw new IllegalArgumentException("attempt is not uncontrolled");
            };
        }
        return new AwaitTimeoutException(message, cause);
    }

    @SuppressWarnings("unchecked")
    private static <Observed, Result> AwaitAttempt.Outcome.Satisfied<Observed, Result> satisfied(AwaitAttempt<Observed, Result> attempt) {
        return (AwaitAttempt.Outcome.Satisfied<Observed, Result>) attempt.outcome();
    }
}
