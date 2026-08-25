package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.await.AwaitResult;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitEngine;
import io.github.gromoff97.awium.engine.WaitOutcome;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitStabilizationException;
import io.github.gromoff97.awium.exceptions.AwaitFailure.AwaitTimeoutException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitConditionEvaluationException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitInterruptedException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitSourceRetrievalException;
import io.github.gromoff97.awium.exceptions.AwaitUncontrolledException.AwaitUnhandledException;

import java.util.function.Supplier;

import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
public final class FailureFactory {

    private FailureFactory() {
        throw new AssertionError("Utility class");
    }

    public static <S, R> R complete(WaitOutcome<S, R> outcome,
            Supplier<String> description, String explanation,
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
            Supplier<String> description, String explanation,
            WaitConfiguration configuration) {
        if (execution.outcome() instanceof WaitOutcome.Satisfied<S, R> success) {
            return new AwaitResult.Satisfied<>(execution.attempts(), execution.totalAttempts(),
                    satisfied(success.attempt()).result());
        }
        return new AwaitResult.Failed<>(execution.attempts(), execution.totalAttempts(),
                failure(execution.outcome(), description, explanation, configuration));
    }

    private static <S, R> Throwable failure(WaitOutcome<S, R> outcome,
            Supplier<String> description, String explanation,
            WaitConfiguration configuration) {
        Throwable cause = terminalCause(outcome.attempt());
        if (cause instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        FailureMessageRenderer.Result rendered;
        try {
            rendered = FailureMessageRenderer.render(
                    outcome, description, explanation, configuration, cause);
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
        if (outcome instanceof WaitOutcome.StabilityLoss<S, R>) {
            return new AwaitStabilizationException(message, cause);
        }
        if (outcome instanceof WaitOutcome.Uncontrolled<S, R>) {
            if (cause instanceof InterruptedException) {
                return new AwaitInterruptedException(message, cause);
            }
            return switch (origin(outcome.attempt())) {
                case SOURCE -> new AwaitSourceRetrievalException(message, cause);
                case CONDITION -> new AwaitConditionEvaluationException(message, cause);
                case WAITING -> new AwaitUnhandledException(message, cause);
            };
        }
        return new AwaitTimeoutException(message, cause);
    }

    private static Throwable terminalCause(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> ignored -> null;
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> ignored -> null;
            case AwaitAttempt.Outcome.AssertionUnsatisfied<?, ?> value -> value.assertion();
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value -> value.failure();
        };
    }

    private static Origin origin(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored -> Origin.WAITING;
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> ignored -> Origin.SOURCE;
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> ignored -> Origin.SOURCE;
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> ignored -> Origin.CONDITION;
            default -> throw new IllegalArgumentException("attempt is not uncontrolled");
        };
    }

    @SuppressWarnings("unchecked")
    private static <S, R> AwaitAttempt.Outcome.Satisfied<S, R> satisfied(
            AwaitAttempt<S, R> attempt) {
        return (AwaitAttempt.Outcome.Satisfied<S, R>) attempt.outcome();
    }

    private static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    private enum Origin { WAITING, SOURCE, CONDITION }
}
