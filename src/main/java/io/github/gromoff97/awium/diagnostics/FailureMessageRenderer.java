package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;

import java.util.Locale;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.engine.WaitConfiguration.duration;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.AfterObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Uncontrolled.BeforeObservation;
import static io.github.gromoff97.awium.engine.WaitOutcome.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateSatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateUnsatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static io.github.gromoff97.awium.engine.WaitOutcome.TimeoutBetweenObservations;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class FailureMessageRenderer {

    private FailureMessageRenderer() {
        throw new AssertionError("Utility class");
    }

    static Result render(WaitOutcome<?> outcome, Supplier<String> description,
            String explanation, WaitConfiguration configuration, Throwable outcomeCause) {
        Context context = new Context(outcome, description, explanation, configuration, outcomeCause);
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
            case TimeoutBetweenObservations<?> value -> message(context, value.attempt(),
                    "Acquisition deadline elapsed before the next attempt");
            case LateUnsatisfiedTimeout<?> value -> message(context, value.attempt(),
                    "Condition remained unsatisfied at or after the acquisition deadline");
            case LateSatisfiedTimeout<?> value -> message(context, value.attempt(),
                    "Condition became satisfied at or after the acquisition deadline");
            case StabilityLoss<?> value -> message(context, value.attempt(),
                    "Condition did not remain stable for the required duration");
            case Uncontrolled<?> value -> message(context, value, uncontrolledHeading(value));
            case Satisfied<?> ignored ->
                    throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private static String emergency(Context context, Throwable failure) {
        StringBuilder out = heading("Failure diagnostics could not be formatted");
        condition(out, context.description == null ? "condition description unavailable" : context.description,
                context.explanation);
        WaitOutcome.Attempt<?> attempt = context.outcome.attempt();
        String actual = null;
        if (!(attempt instanceof BeforeObservation<?>)) {
            actual = context.actual == null ? "<value unavailable: diagnostics failed>" : context.actual;
        }
        String mismatch = attempt instanceof Unsatisfied<?> value ? value.mismatch() : null;
        attempt(out, attempt.number(), "diagnostics", actual, mismatch);
        timing(out, context);
        cause(out, emergencyDiagnostic(failure));
        return finish(out);
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

    private static String message(Context context, WaitOutcome.Attempt<?> attempt, String title) {
        ThrowableDiagnostic assertionDiagnostic = attempt instanceof Unsatisfied<?> unsatisfied
                && unsatisfied.assertionCause() != null
                ? context.causeDiagnostic() : null;
        StringBuilder out = heading(title);
        condition(out, context.conditionDescription(), context.explanation);
        String actual = attempt instanceof BeforeObservation<?> ? null : context.actualValue();
        String origin = attempt instanceof Uncontrolled<?> uncontrolled
                ? uncontrolled.origin().name().toLowerCase(Locale.ROOT) : null;
        String mismatch = attempt instanceof Unsatisfied<?> unsatisfied ? unsatisfied.mismatch() : null;
        attempt(out, attempt.number(), origin, actual, mismatch);
        timing(out, context);
        if (attempt instanceof Uncontrolled<?>) {
            cause(out, context.causeDiagnostic());
        } else if (assertionDiagnostic != null) {
            cause(out, assertionDiagnostic);
        }
        return finish(out);
    }

    private static void timing(StringBuilder out, Context context) {
        out.append('\n').append("Timing:\n");
        field(out, "Acquisition timeout", duration(context.configuration.upToNanos()));
        switch (context.outcome) {
            case TimeoutBetweenObservations<?> outcome -> {
                field(out, "Last attempt completed after",
                        duration(outcome.attempt().completedNanos() - outcome.startedNanos()));
                elapsed(out, outcome.startedNanos(), outcome.completedNanos());
            }
            case LateUnsatisfiedTimeout<?> outcome ->
                    elapsed(out, outcome.startedNanos(), outcome.attempt().completedNanos());
            case LateSatisfiedTimeout<?> outcome ->
                    elapsed(out, outcome.startedNanos(), outcome.attempt().completedNanos());
            case StabilityLoss<?> outcome -> {
                field(out, "Acquired after", duration(outcome.acquiredNanos() - outcome.startedNanos()));
                field(out, "Required stability", duration(context.configuration.stableForNanos()));
                field(out, "Failure detected after",
                        duration(outcome.attempt().completedNanos() - outcome.acquiredNanos()));
                elapsed(out, outcome.startedNanos(), outcome.attempt().completedNanos());
            }
            case Uncontrolled<?> ignored -> {}
            case Satisfied<?> ignored -> {}
        }
        field(out, "Polling interval", duration(context.configuration.everyNanos()));
    }

    private static void elapsed(StringBuilder out, long startedNanos, long completedNanos) {
        field(out, "Elapsed", duration(completedNanos - startedNanos));
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

    private static String renderValue(Object value) {
        if (value != null && value.getClass().isArray()) {
            String array = deepToString(new Object[]{value});
            return array.substring(1, array.length() - 1);
        }
        return requireNonNull(String.valueOf(value), "actual toString() must not return null");
    }

    private static String typeName(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
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

    private static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    record Result(String message, Throwable failure) {}

    private static final class Context {

        private final WaitOutcome<?> outcome;
        private final Supplier<String> descriptionSupplier;
        private final String explanation;
        private final WaitConfiguration configuration;
        private final Throwable outcomeCause;

        private String description;
        private String actual;

        private Context(WaitOutcome<?> outcome, Supplier<String> description,
                String explanation, WaitConfiguration configuration, Throwable outcomeCause) {
            this.outcome = requireNonNull(outcome, "outcome must not be null");
            this.descriptionSupplier = requireNonNull(description, "condition description must not be null");
            this.explanation = explanation;
            this.configuration = requireNonNull(configuration, "configuration must not be null");
            this.outcomeCause = outcomeCause;
        }

        private String conditionDescription() {
            String rendered = requireNonNull(descriptionSupplier.get(),
                    "condition description must not be null");
            if (rendered.isBlank()) {
                throw new IllegalArgumentException("condition description must not be blank");
            }
            return description = rendered;
        }

        private String actualValue() {
            return actual = renderValue(switch (outcome.attempt()) {
                case Satisfied<?> value -> value.actual();
                case Unsatisfied<?> value -> value.actual();
                case AfterObservation<?> value -> value.actual();
                case BeforeObservation<?> ignored ->
                        throw new IllegalArgumentException("attempt has no observed actual");
            });
        }

        private ThrowableDiagnostic causeDiagnostic() {
            return throwableDiagnostic(outcomeCause);
        }
    }

    private record ThrowableDiagnostic(String type, String message) {}
}
