package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitCompletion;

import static io.github.gromoff97.awium.engine.WaitConfiguration.duration;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class FailureMessageRenderer {

    private static final int CAUSE_MESSAGE_LIMIT = 160;
    private static final String STACK_TRACE_HINT = "… <see stack trace>";

    private FailureMessageRenderer() {
        throw new AssertionError("Utility class");
    }

    static Result render(WaitCompletion<?, ?> outcome, String description,
            String explanation, WaitConfiguration configuration,
            AttemptDiagnostic diagnostic) {
        Context context = new Context(outcome, description, explanation,
                configuration, diagnostic);
        Throwable outcomeCause = diagnostic.failure();
        try {
            return new Result(format(context), null);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            addSuppressed(fatal, outcomeCause);
            throw fatal;
        } catch (Throwable failure) {
            try {
                return new Result(emergency(context, failure), failure);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                addSuppressed(fatal, failure);
                if (outcomeCause != failure) {
                    addSuppressed(fatal, outcomeCause);
                }
                throw fatal;
            }
        }
    }

    private static String format(Context context) {
        return switch (context.outcome) {
            case WaitCompletion.TimeoutBetweenObservations<?, ?> ignored ->
                    message(context, "Acquisition deadline elapsed before the next attempt");
            case WaitCompletion.LateTimeout<?, ?> value -> message(context,
                    value.attempt().outcome() instanceof AwaitAttempt.Outcome.Satisfied<?, ?>
                            ? "Condition became satisfied at or after the acquisition deadline"
                            : "Condition remained unsatisfied at or after the acquisition deadline");
            case WaitCompletion.PersistenceFailure<?, ?> ignored ->
                    message(context, "Condition did not persist for the required duration");
            case WaitCompletion.Uncontrolled<?, ?> ignored ->
                    message(context, context.diagnostic.heading());
            case WaitCompletion.Satisfied<?, ?> ignored ->
                    throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private static String emergency(Context context, Throwable failure) {
        StringBuilder out = heading("Failure diagnostics could not be formatted");
        condition(out, context.description, context.explanation);
        AwaitAttempt<?, ?> attempt = context.outcome.attempt();
        String actual = attempt.outcome().timing() instanceof AwaitAttempt.Timing.AfterObservation
                ? context.actual == null
                        ? "<value unavailable: diagnostics failed>" : context.actual
                : null;
        attempt(out, attempt.number(), actual,
                context.diagnostic.sequence() == null ? context.diagnostic.mismatch() : null);
        sequence(out, context.diagnostic);
        timing(out, context);
        cause(out, emergencyDiagnostic(failure));
        return finish(out);
    }

    private static String message(Context context, String title) {
        AwaitAttempt<?, ?> attempt = context.outcome.attempt();
        StringBuilder out = heading(title);
        condition(out, context.description, context.explanation);
        String actual = attempt.outcome().timing() instanceof AwaitAttempt.Timing.AfterObservation
                ? context.actualValue() : null;
        attempt(out, attempt.number(), actual,
                context.diagnostic.sequence() == null ? context.diagnostic.mismatch() : null);
        sequence(out, context.diagnostic);
        if (!(context.outcome instanceof WaitCompletion.Uncontrolled<?, ?>)) {
            timing(out, context);
        }
        Throwable failure = context.diagnostic.failure();
        if (failure != null) {
            cause(out, throwableDiagnostic(failure));
        }
        return finish(out);
    }

    private static void timing(StringBuilder out, Context context) {
        out.append('\n').append("Timing:\n");
        field(out, "Acquisition timeout",
                duration(context.configuration.upToNanos()));
        long completedAfter = context.outcome.attempt().outcome().timing().completionOffset().toNanos();
        switch (context.outcome) {
            case WaitCompletion.TimeoutBetweenObservations<?, ?> outcome -> {
                field(out, "Last attempt completed after", duration(completedAfter));
                field(out, "Elapsed", duration(outcome.elapsedNanos()));
            }
            case WaitCompletion.LateTimeout<?, ?> outcome ->
                    field(out, "Elapsed", duration(completedAfter));
            case WaitCompletion.PersistenceFailure<?, ?> outcome -> {
                long acquiredAfter = outcome.acquiredAfterNanos();
                field(out, "Acquired after", duration(acquiredAfter));
                field(out, "Required persistence",
                        duration(context.configuration.persistenceNanos()));
                field(out, "Failure detected after", duration(completedAfter - acquiredAfter));
            }
            case WaitCompletion.Uncontrolled<?, ?> ignored -> {}
            case WaitCompletion.Satisfied<?, ?> ignored -> {}
        }
        field(out, "Polling interval",
                duration(context.configuration.everyNanos()));
    }

    private static void attempt(StringBuilder out, long number, String actual,
            String mismatch) {
        out.append('\n').append("Attempt: ").append(number).append('\n');
        if (actual != null) {
            field(out, "Actual", actual);
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
        }
    }

    private static void cause(StringBuilder out, ThrowableDiagnostic cause) {
        out.append('\n');
        field(out, "", "Cause", cause.type());
        if (cause.message() != null) {
            field(out, "Message", cause.message());
        }
    }

    private static void condition(StringBuilder out, String expectation,
            String importance) {
        field(out, "", "Condition", expectation);
        if (importance != null) {
            field(out, "Importance", importance);
        }
    }

    private static void sequence(StringBuilder out, AttemptDiagnostic diagnostic) {
        Evaluation.Context.Sequence sequence = diagnostic.sequence();
        if (sequence == null) {
            return;
        }
        out.append('\n').append("Sequence (captured ").append(sequence.capturedStages())
                .append(" of ").append(sequence.totalStages()).append("):\n");
        field(out, "Expectation", sequence.expectation());
        if (sequence.importance() != null) {
            field(out, "Importance", sequence.importance());
        }
        if (diagnostic.mismatch() != null) {
            field(out, "Mismatch", diagnostic.mismatch());
        }
    }

    private static void field(StringBuilder out, String label, String value) {
        field(out, "    ", label, value);
    }

    private static void field(StringBuilder out, String indentation,
            String label, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("\n")) {
            out.append(indentation).append(label).append(": ")
                    .append(normalized).append('\n');
            return;
        }
        out.append(indentation).append(label).append(":\n");
        for (String line : normalized.split("\n", -1)) {
            if (!line.isEmpty()) {
                out.append(indentation).append("    ").append(line);
            }
            out.append('\n');
        }
    }

    private static StringBuilder heading(String value) {
        return new StringBuilder(value).append("\n\n");
    }

    private static String finish(StringBuilder out) {
        return out.deleteCharAt(out.length() - 1).toString();
    }

    private static String renderValue(Object value) {
        if (value != null && value.getClass().isArray()) {
            String array = deepToString(new Object[]{value});
            return array.substring(1, array.length() - 1);
        }
        return requireNonNull(String.valueOf(value),
                "actual toString() must not return null");
    }

    private static String typeName(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
    }

    private static ThrowableDiagnostic throwableDiagnostic(Throwable failure) {
        return new ThrowableDiagnostic(typeName(failure), causeMessage(failure));
    }

    private static String causeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.codePointCount(0, normalized.length()) <= CAUSE_MESSAGE_LIMIT) {
            return normalized;
        }
        int prefixLength = CAUSE_MESSAGE_LIMIT
                - STACK_TRACE_HINT.codePointCount(0, STACK_TRACE_HINT.length());
        return normalized.substring(0, normalized.offsetByCodePoints(0, prefixLength))
                .stripTrailing() + STACK_TRACE_HINT;
    }

    private static ThrowableDiagnostic emergencyDiagnostic(Throwable failure) {
        try {
            return throwableDiagnostic(failure);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable ignored) {
            return new ThrowableDiagnostic(typeName(failure), null);
        }
    }

    static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    private static Evaluation.Context.Sequence sequence(Evaluation.Context context) {
        return context instanceof Evaluation.Context.Sequence value ? value : null;
    }

    static AttemptDiagnostic diagnostic(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> value ->
                    new AttemptDiagnostic(value.observed(), null, null, null, null);
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value ->
                    new AttemptDiagnostic(value.observed(), value.mismatch(),
                            sequence(value.context()), value.assertion(), null);
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
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value ->
                    uncontrolled(value.observed(), sequence(value.context()), value.failure(),
                            "Caller thread was interrupted during condition evaluation",
                            "Condition evaluation failed");
        };
    }

    private static AttemptDiagnostic uncontrolled(Object observed,
            Evaluation.Context.Sequence sequence, Throwable failure,
            String interruptedHeading, String failureHeading) {
        return new AttemptDiagnostic(observed, null, sequence, failure,
                failure instanceof InterruptedException ? interruptedHeading : failureHeading);
    }

    record Result(String message, Throwable failure) {}

    private static final class Context {

        private final WaitCompletion<?, ?> outcome;
        private final String description;
        private final String explanation;
        private final WaitConfiguration configuration;
        private final AttemptDiagnostic diagnostic;

        private String actual;

        private Context(WaitCompletion<?, ?> outcome,
                String description, String explanation,
                WaitConfiguration configuration, AttemptDiagnostic diagnostic) {
            this.outcome = requireNonNull(outcome, "outcome must not be null");
            this.description = requireNonNull(description,
                    "condition description must not be null");
            this.explanation = explanation;
            this.configuration = requireNonNull(configuration,
                    "configuration must not be null");
            this.diagnostic = requireNonNull(diagnostic,
                    "attempt diagnostic must not be null");
        }

        private String actualValue() {
            return actual = renderValue(diagnostic.observed());
        }
    }

    record AttemptDiagnostic(Object observed, String mismatch,
            Evaluation.Context.Sequence sequence, Throwable failure,
            String heading) {}

    private record ThrowableDiagnostic(String type, String message) {}
}
