package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;

import java.util.Locale;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.AfterObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.BeforeObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateSatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateUnsatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static io.github.gromoff97.awium.engine.WaitOutcome.TimeoutBetweenObservations;
import static io.github.gromoff97.awium.engine.WaitConfiguration.duration;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
public final class FailureMessage {

    private static final String DESCRIPTION_UNAVAILABLE = "condition description unavailable";

    private FailureMessage() {
        throw new AssertionError("Utility class");
    }

    static Rendering render(WaitOutcome<?> outcome, Supplier<String> description, String explanation, WaitConfiguration configuration) {
        Context context = new Context(outcome, description, explanation, configuration);
        try {
            return new Rendering(format(context), null);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            suppress(fatal, terminalCause(context.outcome));
            throw fatal;
        } catch (Throwable failure) {
            try {
                return new Rendering(emergency(context, failure), failure);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                suppress(fatal, failure);
                Throwable engineCause = terminalCause(context.outcome);
                if (engineCause != failure) {
                    suppress(fatal, engineCause);
                }
                throw fatal;
            }
        }
    }

    private static String format(Context context) {
        return switch (context.outcome) {
            case TimeoutBetweenObservations<?> value -> message(context, value.attempt(),
                    "Acquisition deadline elapsed before the next attempt");
            case LateUnsatisfiedTimeout<?> value -> message(context, value.attempt(),
                    "Condition remained unsatisfied at or after the acquisition deadline");
            case LateSatisfiedTimeout<?> value -> message(context, value.attempt(),
                    "Condition became satisfied at or after the acquisition deadline");
            case StabilityLoss<?> value -> message(context, value.attempt(),
                    "Condition did not remain stable for the required duration");
            case Uncontrolled<?> value -> message(context, value, uncontrolledHeading(value));
            case Satisfied<?> ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private static String emergency(Context context, Throwable failure) {
        StringBuilder out = heading("Failure diagnostics could not be formatted");
        condition(out, context.description == null ? DESCRIPTION_UNAVAILABLE : context.description, context.explanation);
        String actual = context.outcome.attempt() instanceof BeforeObservation<?>
                ? null : context.actual == null
                        ? "<value unavailable: diagnostics failed>"
                        : context.actual;
        String mismatch = context.outcome.attempt() instanceof Unsatisfied<?> value
                ? value.mismatch() : null;
        attempt(out, context.outcome.attempt().number(), "diagnostics", actual, mismatch);
        timing(out, context);
        cause(out, emergencyDiagnostic(failure));
        return finish(out);
    }

    private static String message(Context context, Unsatisfied<?> attempt, String heading) {
        ThrowableDiagnostic cause = attempt.assertionCause() == null
                ? null : context.causeDiagnostic();
        return message(context, attempt, heading, cause);
    }

    private static String uncontrolledHeading(Uncontrolled<?> attempt) {
        return attempt.cause() instanceof InterruptedException
                ? "Caller thread was interrupted"
                : switch (attempt.origin()) {
            case SOURCE -> "Source retrieval failed";
            case CONDITION -> "Condition evaluation failed";
            case WAITING -> "Waiting before the next attempt failed";
        };
    }

    private static String message(Context context, WaitOutcome.Attempt<?> attempt, String heading) {
        return message(context, attempt, heading, null);
    }

    private static String message(Context context, WaitOutcome.Attempt<?> attempt, String heading,
            ThrowableDiagnostic diagnostic) {
        StringBuilder out = heading(heading);
        condition(out, context);
        String actual = attempt instanceof BeforeObservation<?>
                ? null : context.actualValue();
        String origin = attempt instanceof Uncontrolled<?> uncontrolled
                ? uncontrolled.origin().name().toLowerCase(Locale.ROOT) : null;
        String mismatch = attempt instanceof Unsatisfied<?> unsatisfied
                ? unsatisfied.mismatch() : null;
        attempt(out, attempt.number(), origin, actual, mismatch);
        timing(out, context);
        if (attempt instanceof Uncontrolled<?>) {
            cause(out, context.causeDiagnostic());
        } else if (diagnostic != null) {
            cause(out, diagnostic);
        }
        return finish(out);
    }

    private static void timing(StringBuilder out, Context context) {
        out.append('\n').append("Timing:\n");
        switch (context.outcome) {
            case TimeoutBetweenObservations<?> outcome -> {
                acquisitionTimeout(out, context);
                field(out, "Last attempt completed after", duration(outcome.attempt().completedNanos() - outcome.startedNanos()));
                field(out, "Elapsed", duration(outcome.completedNanos() - outcome.startedNanos()));
                pollingInterval(out, context);
            }
            case LateUnsatisfiedTimeout<?> outcome -> timeoutTiming(out, context, outcome.startedNanos(), outcome.attempt().completedNanos());
            case LateSatisfiedTimeout<?> outcome -> timeoutTiming(out, context, outcome.startedNanos(), outcome.attempt().completedNanos());
            case StabilityLoss<?> outcome -> {
                acquisitionTimeout(out, context);
                field(out, "Acquired after", duration(outcome.acquiredNanos() - outcome.startedNanos()));
                field(out, "Required stability", duration(context.configuration.stableForNanos()));
                field(out, "Failure detected after", duration(outcome.attempt().completedNanos() - outcome.acquiredNanos()));
                field(out, "Elapsed", duration(outcome.attempt().completedNanos() - outcome.startedNanos()));
                pollingInterval(out, context);
            }
            case Uncontrolled<?> ignored -> {
                acquisitionTimeout(out, context);
                pollingInterval(out, context);
            }
            case Satisfied<?> ignored -> {
            }
        }
    }

    private static void timeoutTiming(StringBuilder out, Context context, long startedNanos, long completedNanos) {
        acquisitionTimeout(out, context);
        field(out, "Elapsed", duration(completedNanos - startedNanos));
        pollingInterval(out, context);
    }

    private static void acquisitionTimeout(StringBuilder out, Context context) {
        field(out, "Acquisition timeout", duration(context.configuration.upToNanos()));
    }

    private static void pollingInterval(StringBuilder out, Context context) {
        field(out, "Polling interval", duration(context.configuration.everyNanos()));
    }

    private static void attempt(StringBuilder out, long number, String origin, String actual, String mismatch) {
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

    private static void condition(StringBuilder out, Context context) {
        condition(out, context.conditionDescription(), context.explanation);
    }

    private static void condition(StringBuilder out, String expectation, String importance) {
        out.append("Condition:\n");
        field(out, "Expectation", expectation);
        if (importance != null) {
            field(out, "Importance", importance);
        }
    }

    private static void field(StringBuilder out, String label, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.contains("\n")) {
            out.append("    ").append(label).append(": ").append(normalized).append('\n');
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

    private static String render(Object value) {
        if (value != null && value.getClass().isArray()) {
            String array = deepToString(new Object[] {value});
            return array.substring(1, array.length() - 1);
        }
        return requireNonNull(String.valueOf(value), "actual toString() must not return null");
    }

    private static String typeName(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
    }

    static Throwable terminalCause(WaitOutcome<?> outcome) {
        return switch (outcome.attempt()) {
            case Satisfied<?> ignored -> null;
            case Unsatisfied<?> attempt -> attempt.assertionCause();
            case Uncontrolled<?> attempt -> attempt.cause();
        };
    }

    static void suppress(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    record Rendering(String message, Throwable failure) {}

    private static final class Context {

        private final WaitOutcome<?> outcome;
        private final Supplier<String> descriptionSupplier;
        private final String explanation;
        private final WaitConfiguration configuration;

        private String description;
        private String actual;

        private Context(WaitOutcome<?> outcome, Supplier<String> description, String explanation, WaitConfiguration configuration) {
            this.outcome = requireNonNull(outcome, "outcome must not be null");
            this.descriptionSupplier = requireNonNull(description, "condition description must not be null");
            this.explanation = explanation;
            this.configuration = requireNonNull(configuration, "configuration must not be null");
        }

        private String conditionDescription() {
            String rendered = requireNonNull(descriptionSupplier.get(), "condition description must not be null");
            if (rendered.isBlank()) {
                throw new IllegalArgumentException("condition description must not be blank");
            }
            return description = rendered;
        }

        private String actualValue() {
            return actual = render(switch (outcome.attempt()) {
                case Satisfied<?> value -> value.actual();
                case Unsatisfied<?> value -> value.actual();
                case AfterObservation<?> value -> value.actual();
                case BeforeObservation<?> ignored -> throw new IllegalArgumentException("attempt has no observed actual");
            });
        }

        private ThrowableDiagnostic causeDiagnostic() {
            return throwableDiagnostic(terminalCause(outcome));
        }
    }

    private static ThrowableDiagnostic throwableDiagnostic(Throwable failure) {
        String message = failure.getMessage();
        return new ThrowableDiagnostic(typeName(failure), message == null || message.isBlank() ? null : message);
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

    private record ThrowableDiagnostic(String type, String message) {}

}
