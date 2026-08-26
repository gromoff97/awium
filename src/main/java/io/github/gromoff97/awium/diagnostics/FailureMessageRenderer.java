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
                    message(context, value.attempt(), uncontrolledHeading(value.attempt()));
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
        attempt(out, attempt.number(), "diagnostics", actual,
                sequence(attempt) == null ? mismatch(attempt) : null);
        sequence(out, attempt);
        timing(out, context);
        cause(out, emergencyDiagnostic(failure));
        return finish(out);
    }

    private static String uncontrolledHeading(AwaitAttempt<?, ?> attempt) {
        Throwable failure = failure(attempt);
        if (failure instanceof InterruptedException) {
            return "Caller thread was interrupted";
        }
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored ->
                    "Waiting before the next attempt failed";
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> ignored ->
                    "Source retrieval failed";
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> ignored ->
                    "Source retrieval failed";
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> ignored ->
                    "Condition evaluation failed";
            default -> throw new IllegalArgumentException("attempt is not uncontrolled");
        };
    }

    private static String message(Context context, AwaitAttempt<?, ?> attempt,
            String title) {
        StringBuilder out = heading(title);
        condition(out, context.description, context.explanation);
        String actual = attempt.outcome().timing() instanceof AwaitAttempt.Timing.AfterObservation
                ? context.actualValue() : null;
        attempt(out, attempt.number(), origin(attempt), actual,
                sequence(attempt) == null ? mismatch(attempt) : null);
        sequence(out, attempt);
        timing(out, context);
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
                field(out, "Elapsed", duration(completedAfter));
            }
            case WaitOutcome.Uncontrolled<?, ?> ignored -> {}
            case WaitOutcome.Satisfied<?, ?> ignored -> {}
        }
        field(out, "Polling interval",
                duration(context.configuration.everyNanos()));
    }

    private static void attempt(StringBuilder out, long number, String origin,
            String actual, String mismatch) {
        out.append('\n').append("Attempt:\n");
        field(out, "Number", Long.toString(number));
        if (origin != null) {
            field(out, "Origin", origin);
        }
        if (actual != null) {
            field(out, "Actual", actual);
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
        }
    }

    private static void cause(StringBuilder out, ThrowableDiagnostic cause) {
        out.append('\n').append("Cause:\n");
        field(out, "Type", cause.type());
        if (cause.message() != null) {
            field(out, "Message", cause.message());
        }
    }

    private static void condition(StringBuilder out, String expectation,
            String importance) {
        out.append("Condition:\n");
        field(out, "Expectation", expectation);
        if (importance != null) {
            field(out, "Importance", importance);
        }
    }

    private static void sequence(StringBuilder out, AwaitAttempt<?, ?> attempt) {
        Evaluation.Context.Sequence sequence = sequence(attempt);
        if (sequence == null) {
            return;
        }
        out.append('\n').append("Sequence:\n");
        field(out, "Caught", sequence.caught() + " of " + sequence.total());
        field(out, "Waiting for", sequence.stage() + " of " + sequence.total());
        field(out, "Expectation", sequence.expectation());
        if (sequence.importance() != null) {
            field(out, "Importance", sequence.importance());
        }
        if (mismatch(attempt) != null) {
            field(out, "Mismatch", mismatch(attempt));
        }
    }

    private static void field(StringBuilder out, String label, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("\n")) {
            out.append("    ").append(label).append(": ")
                    .append(normalized).append('\n');
            return;
        }
        out.append("    ").append(label).append(":\n");
        for (String line : normalized.split("\n", -1)) {
            if (!line.isEmpty()) {
                out.append("        ").append(line);
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
        String message = failure.getMessage();
        return new ThrowableDiagnostic(typeName(failure),
                message == null || message.isBlank() ? null : message);
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

    private static Object observed(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Satisfied<?, ?> value -> value.observed();
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value -> value.observed();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value -> value.observed();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value -> value.observed();
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored ->
                    throw new IllegalArgumentException("attempt has no observed actual");
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> ignored ->
                    throw new IllegalArgumentException("attempt has no observed actual");
        };
    }

    private static String mismatch(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value -> value.mismatch();
            default -> null;
        };
    }

    private static Evaluation.Context.Sequence sequence(AwaitAttempt<?, ?> attempt) {
        Evaluation.Context context = switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value -> value.context();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value -> value.context();
            default -> Evaluation.Context.Plain.INSTANCE;
        };
        return context instanceof Evaluation.Context.Sequence value ? value : null;
    }

    static Throwable failure(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.Unsatisfied<?, ?> value -> value.assertion();
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value -> value.failure();
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> value -> value.failure();
            default -> null;
        };
    }

    private static String origin(AwaitAttempt<?, ?> attempt) {
        return switch (attempt.outcome()) {
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored -> "waiting";
            case AwaitAttempt.Outcome.SourceRetrievalFailed<?, ?> ignored -> "source";
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> ignored -> "source";
            case AwaitAttempt.Outcome.ConditionEvaluationFailed<?, ?> ignored -> "condition";
            default -> null;
        };
    }

    record Result(String message, Throwable failure) {}

    private static final class Context {

        private final WaitOutcome<?, ?> outcome;
        private final String description;
        private final String explanation;
        private final WaitConfiguration configuration;
        private final Throwable outcomeCause;

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
        }

        private String actualValue() {
            return actual = renderValue(observed(outcome.attempt()));
        }
    }

    private record ThrowableDiagnostic(String type, String message) {}
}
