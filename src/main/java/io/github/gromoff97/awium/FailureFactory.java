package io.github.gromoff97.awium;

import java.util.List;

import static io.github.gromoff97.awium.AwaitFailure.Reason.*;
import static java.lang.Thread.currentThread;

@SuppressWarnings("removal")
final class FailureFactory {

    private FailureFactory() {
        throw new AssertionError("Utility class");
    }

    static <Observed, Result> Result complete(WaitCompletion<Observed, Result> outcome,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<Observed, Result> success) {
            return satisfied(success.attempt()).result();
        }
        AwaitFailure failure = failure(outcome, metadata, configuration);
        switch (failure.reason()) {
            case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
            default -> throw new AwaitExecutionException(failure);
        }
    }

    static <Observed, Result> AwaitResult<Observed, Result> capture(WaitCompletion<Observed, Result> outcome,
            List<AwaitAttempt<Observed, Result>> attempts,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<Observed, Result> success) {
            return new AwaitResult.Satisfied<>(attempts, success.attempt().number(),
                    satisfied(success.attempt()).result());
        }
        return new AwaitResult.Failed<>(attempts, outcome.attempt().number(),
                failure(outcome, metadata, configuration));
    }

    private static <Observed, Result> AwaitFailure failure(WaitCompletion<Observed, Result> outcome,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        AttemptDiagnostic diagnostic = diagnostic(outcome.attempt());
        if (diagnostic.context() instanceof ConditionResult.Context.Expectation expectation) {
            metadata = new ConditionMetadata(expectation.description(), metadata.explanation(), expectation.reference());
        }
        Throwable cause = diagnostic.failure();
        if (cause instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted()
                || cause instanceof InterruptedException;
        FailureMessageRenderer.Result rendered;
        try {
            rendered = FailureMessageRenderer.render(outcome, metadata, configuration, diagnostic);
            restoreInterrupt |= rendered.failure() instanceof InterruptedException;
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }

        Throwable renderingFailure = rendered.failure();
        if (renderingFailure != null) {
            return new AwaitFailure(DIAGNOSTICS_FAILED, rendered.message(), renderingFailure,
                    cause == null || cause == renderingFailure ? List.of() : List.of(cause));
        }

        AwaitFailure.Reason reason = switch (outcome) {
            case WaitCompletion.PersistenceFailure<?, ?> ignored -> PERSISTENCE_FAILED;
            case WaitCompletion.Uncontrolled<?, ?> ignored -> cause instanceof InterruptedException
                    ? INTERRUPTED : switch (outcome.attempt().outcome()) {
                        case AwaitAttempt.Outcome.WaitingFailed<?, ?> value -> WAIT_FAILED;
                        case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value -> SOURCE_FAILED;
                        case AwaitAttempt.Outcome.Evaluated<?, ?> value -> CONDITION_FAILED;
                        default -> throw new IllegalArgumentException("attempt is not uncontrolled");
                    };
            default -> TIMEOUT;
        };
        return new AwaitFailure(reason, rendered.message(), cause, List.of());
    }

    private static <Observed, Result> ConditionResult.Satisfied<? extends Result> satisfied(AwaitAttempt<Observed, Result> attempt) {
        var evaluated = (AwaitAttempt.Outcome.Evaluated<Observed, Result>) attempt.outcome();
        return (ConditionResult.Satisfied<? extends Result>) evaluated.evaluation();
    }

    static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    private static AttemptDiagnostic diagnostic(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Evaluated<?, ?> value -> switch (value.evaluation()) {
                case ConditionResult.Satisfied<?> result ->
                        new AttemptDiagnostic(value.observed(), null, result.context(), null, null);
                case ConditionResult.Unsatisfied<?> result ->
                        new AttemptDiagnostic(value.observed(), result.mismatch(),
                                result.context(), result.assertion(), null);
                case ConditionResult.Uncontrolled<?> result ->
                        uncontrolled(value.observed(), result.context(), result.cause(),
                                "Caller thread was interrupted during condition evaluation",
                                "Condition evaluation failed");
            };
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value ->
                    uncontrolled(null, null, value.failure(),
                            "Caller thread was interrupted while waiting",
                            "Waiting before the next attempt failed");
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value ->
                    uncontrolled(null, null, value.failure(),
                            "Caller thread was interrupted during source retrieval",
                            "Source retrieval failed");
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value ->
                    uncontrolled(value.observed(), null, value.failure(),
                            "Caller thread was interrupted during source retrieval",
                            "Source retrieval failed");
        };
    }

    private static AttemptDiagnostic uncontrolled(Object observed,
            ConditionResult.Context context, Throwable failure,
            String interruptedHeading, String failureHeading) {
        return new AttemptDiagnostic(observed, null, context, failure,
                failure instanceof InterruptedException ? interruptedHeading : failureHeading);
    }

    record AttemptDiagnostic(Object observed, String mismatch,
            ConditionResult.Context context, Throwable failure, String heading) {

        ConditionResult.Context.Sequence sequence() {
            return context instanceof ConditionResult.Context.Sequence sequence ? sequence : null;
        }
    }
}
