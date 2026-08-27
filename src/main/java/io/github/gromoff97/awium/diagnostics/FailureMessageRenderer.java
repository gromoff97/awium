package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;

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

    static Result render(WaitOutcome<?, ?> outcome, String description,
            String explanation, WaitConfiguration configuration,
            Throwable outcomeCause) {
        Context context = new Context(outcome, description, explanation,
                configuration, outcomeCause);
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
            case WaitOutcome.TimeoutBetweenObservations<?, ?> value ->
                    message(context, value.attempt(),
                            "Acquisition deadline elapsed before the next attempt");
            case WaitOutcome.LateTimeout<?, ?> value -> message(context, value.attempt(),
                    value.attempt().outcome() instanceof AwaitAttempt.Outcome.Satisfied<?, ?>
                            ? "Condition became satisfied at or after the acquisition deadline"
                            : "Condition remained unsatisfied at or after the acquisition deadline");
            case WaitOutcome.PersistenceFailure<?, ?> value ->
                    message(context, value.attempt(),
                            "Condition did not persist for the required duration");
            case WaitOutcome.Uncontrolled<?, ?> value ->
                    message(context, value.attempt(), uncontrolledHeading(context.diagnostic));
            case WaitOutcome.Satisfied<?, ?> ignored ->
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

    private static String uncontrolledHeading(AttemptDiagnostic diagnostic) {
        Throwable failure = diagnostic.failure();
        if (failure instanceof InterruptedException) {
            return switch (diagnostic.origin()) {
                case "waiting" -> "Caller thread was interrupted while waiting";
                case "source" -> "Caller thread was interrupted during source retrieval";
                case "condition" -> "Caller thread was interrupted during condition evaluation";
                default -> throw new IllegalArgumentException("attempt is not uncontrolled");
            };
        }
        return switch (diagnostic.origin()) {
            case "waiting" -> "Waiting before the next attempt failed";
            case "source" -> "Source retrieval failed";
            case "condition" -> "Condition evaluation failed";
            default -> throw new IllegalArgumentException("attempt is not uncontrolled");
        };
    }

    private static String message(Context context, AwaitAttempt<?, ?> attempt,
            String title) {
        StringBuilder out = heading(title);
        condition(out, context.description, context.explanation);
        String actual = attempt.outcome().timing() instanceof AwaitAttempt.Timing.AfterObservation
                ? context.actualValue() : null;
        attempt(out, attempt.number(), actual,
                context.diagnostic.sequence() == null ? context.diagnostic.mismatch() : null);
        sequence(out, context.diagnostic);
        if (!(context.outcome instanceof WaitOutcome.Uncontrolled<?, ?>)) {
            timing(out, context);
        }
        if (context.outcomeCause != null) {
            cause(out, throwableDiagnostic(context.outcomeCause));
        }
        return finish(out);
    }

    private static void timing(StringBuilder out, Context context) {
        out.append('\n').append("Timing:\n");
        field(out, "Acquisition timeout",
                duration(context.configuration.upToNanos()));
        long completedAfter = context.outcome.attempt().outcome().timing().completionOffset().toNanos();
        switch (context.outcome) {
            case WaitOutcome.TimeoutBetweenObservations<?, ?> outcome -> {
                field(out, "Last attempt completed after", duration(completedAfter));
                field(out, "Elapsed", duration(outcome.elapsedNanos()));
            }
            case WaitOutcome.LateTimeout<?, ?> outcome ->
                    field(out, "Elapsed", duration(completedAfter));
            case WaitOutcome.PersistenceFailure<?, ?> outcome -> {
                long acquiredAfter = outcome.acquiredAfterNanos();
                field(out, "Acquired after", duration(acquiredAfter));
                field(out, "Required persistence",
                        duration(context.configuration.persistenceNanos()));
                field(out, "Failure detected after", duration(completedAfter - acquiredAfter));
            }
            case WaitOutcome.Uncontrolled<?, ?> ignored -> {}
            case WaitOutcome.Satisfied<?, ?> ignored -> {}
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
        out.append('\n').append("Sequence (caught ").append(sequence.caught())
                .append(" of ").append(sequence.total()).append("):\n");
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

    private static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    private static Evaluation.Context.Sequence sequence(Evaluation.Context context) {
        return context instanceof Evaluation.Context.Sequence value ? value : null;
    }

    static Throwable failure(AwaitAttempt<?, ?> attempt) {
        return diagnostic(attempt).failure();
    }

    private static AttemptDiagnostic diagnostic(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> value ->
                    new AttemptDiagnostic(value.observed(), null, null, null, null);
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value ->
                    new AttemptDiagnostic(value.observed(), value.mismatch(),
                            sequence(value.context()), value.assertion(), null);
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value ->
                    new AttemptDiagnostic(null, null, null, value.failure(), "waiting");
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value ->
                    new AttemptDiagnostic(null, null, null, value.failure(), "source");
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value ->
                    new AttemptDiagnostic(value.observed(), null, null, value.failure(), "source");
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value ->
                    new AttemptDiagnostic(value.observed(), null,
                            sequence(value.context()), value.failure(), "condition");
        };
    }

    record Result(String message, Throwable failure) {}

    private static final class Context {

        private final WaitOutcome<?, ?> outcome;
        private final String description;
        private final String explanation;
        private final WaitConfiguration configuration;
        private final Throwable outcomeCause;
        private final AttemptDiagnostic diagnostic;

        private String actual;

        private Context(WaitOutcome<?, ?> outcome,
                String description, String explanation,
                WaitConfiguration configuration, Throwable outcomeCause) {
            this.outcome = requireNonNull(outcome, "outcome must not be null");
            this.description = requireNonNull(description,
                    "condition description must not be null");
            this.explanation = explanation;
            this.configuration = requireNonNull(configuration,
                    "configuration must not be null");
            this.outcomeCause = outcomeCause;
            this.diagnostic = diagnostic(outcome.attempt());
        }

        private String actualValue() {
            return actual = renderValue(diagnostic.observed());
        }
    }

    private record AttemptDiagnostic(Object observed, String mismatch,
            Evaluation.Context.Sequence sequence, Throwable failure,
            String origin) {}

    private record ThrowableDiagnostic(String type, String message) {}
}
