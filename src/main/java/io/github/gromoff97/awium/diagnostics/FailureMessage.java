package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;

import java.util.Locale;
import java.util.function.Function;

import static io.github.gromoff97.awium.engine.Attempt.Satisfied;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.AfterObservation;
import static io.github.gromoff97.awium.engine.Attempt.Uncontrolled.BeforeObservation;
import static io.github.gromoff97.awium.engine.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateSatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.LateUnsatisfiedTimeout;
import static io.github.gromoff97.awium.engine.WaitOutcome.StabilityLoss;
import static io.github.gromoff97.awium.engine.WaitOutcome.TimeoutBetweenObservations;
import static java.util.Arrays.deepToString;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
public final class FailureMessage {

    private static final String DESCRIPTION_UNAVAILABLE =
            "condition description unavailable";
    private static final long[] UNIT_NANOS = {
        86_400_000_000_000L,
        3_600_000_000_000L,
        60_000_000_000L,
        1_000_000_000L,
        1_000_000L,
        1_000L,
        1L
    };
    private static final String[] UNIT_NAMES = {
        "day", "hour", "minute", "second", "millisecond", "microsecond",
        "nanosecond"
    };

    private final Function<Context, String> formatter;

    public FailureMessage() {
        this(FailureMessage::format);
    }

    public FailureMessage(Function<Context, String> formatter) {
        this.formatter = requireNonNull(formatter);
    }

    public String format(WaitOutcome<?> outcome,
            RuntimeCondition<?, ?> condition,
            WaitConfiguration configuration) {
        Context context = new Context(outcome, condition, configuration);
        try {
            return requireNonNull(formatter.apply(context),
                    "diagnostic formatter returned null");
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            throw new FormattingFailure(context, failure);
        }
    }

    public static String configurationConflict(long everyNanos,
            long upToNanos) {
        return "poll interval (" + duration(everyNanos)
                + ") must be shorter than acquisition timeout ("
                + duration(upToNanos) + ")";
    }

    String emergency(FormattingFailure failure) {
        Context context = failure.context;
        StringBuilder out = heading("Await execution was unhandled");
        field(out, 0, "Attempt",
                Long.toString(context.outcome.attempt().number()));
        field(out, 0, "Condition", context.descriptionMaterialized
                ? context.description : DESCRIPTION_UNAVAILABLE);
        if (!(context.outcome.attempt()
                instanceof BeforeObservation<?>)) {
            field(out, 0, "Actual", context.actualMaterialized
                    ? context.actual
                    : "<value unavailable: diagnostics failed>");
        }
        optionalField(out, "Because", context.condition.explanation());
        field(out, 0, "Cause", typeName(failure.getCause()));
        return finish(out);
    }

    private static String format(Context context) {
        return switch (context.outcome) {
            case TimeoutBetweenObservations<?> outcome ->
                    timeoutBetween(context, outcome);
            case LateUnsatisfiedTimeout<?> outcome ->
                    lateUnsatisfied(context, outcome);
            case LateSatisfiedTimeout<?> outcome ->
                    lateSatisfied(context, outcome);
            case StabilityLoss<?> outcome ->
                    stabilityLoss(context, outcome);
            case Uncontrolled<?> outcome ->
                    uncontrolled(context, outcome);
            case Satisfied<?> ignored -> throw new IllegalArgumentException(
                    "successful outcomes have no failure diagnostics");
        };
    }

    private static String timeoutBetween(Context context,
            TimeoutBetweenObservations<?> outcome) {
        Unsatisfied<?> last = outcome.attempt();
        AssertionDiagnostic assertion = last.assertionCause() == null
                ? null : context.assertionDiagnostic();
        StringBuilder out = heading("Await timed out");
        field(out, 0, "Condition", context.conditionDescription());
        field(out, 0, "Reason",
                "acquisition deadline elapsed before the next observation");
        out.append('\n').append("Last observation:\n");
        field(out, 4, "Attempt", Long.toString(last.number()));
        field(out, 4, "Completed after", duration(
                last.completedNanos() - outcome.startedNanos()));
        field(out, 4, "Mismatch", assertion == null
                ? last.mismatch() : assertion.mismatch());
        if (assertion != null) {
            out.append('\n');
            field(out, 0, "Cause", assertion.cause());
        }
        out.append('\n').append("Timing:\n");
        timeoutTiming(out, context, outcome.startedNanos(),
                outcome.completedNanos(), false);
        return finish(out);
    }

    private static String lateUnsatisfied(Context context,
            LateUnsatisfiedTimeout<?> outcome) {
        Unsatisfied<?> attempt = outcome.attempt();
        AssertionDiagnostic assertion = attempt.assertionCause() == null
                ? null : context.assertionDiagnostic();
        StringBuilder out = heading("Await timed out");
        field(out, 0, "Condition", context.conditionDescription());
        field(out, 0, "Observed", context.actualValue());
        field(out, 0, "Mismatch", assertion == null
                ? attempt.mismatch() : assertion.mismatch());
        optionalField(out, "Because", context.condition.explanation());
        if (assertion != null) {
            field(out, 0, "Cause", assertion.cause());
        }
        out.append('\n').append("Timing:\n");
        timeoutTiming(out, context, outcome.startedNanos(),
                outcome.attempt().completedNanos(), true);
        return finish(out);
    }

    private static String lateSatisfied(Context context,
            LateSatisfiedTimeout<?> outcome) {
        StringBuilder out = heading("Await timed out");
        field(out, 0, "Condition", context.conditionDescription());
        field(out, 0, "Observed", context.actualValue());
        field(out, 0, "Reason", "condition became satisfied after the timeout");
        optionalField(out, "Because", context.condition.explanation());
        out.append('\n').append("Timing:\n");
        timeoutTiming(out, context, outcome.startedNanos(),
                outcome.attempt().completedNanos(), true);
        return finish(out);
    }

    private static String stabilityLoss(Context context,
            StabilityLoss<?> outcome) {
        Unsatisfied<?> attempt = outcome.attempt();
        AssertionDiagnostic assertion = attempt.assertionCause() == null
                ? null : context.assertionDiagnostic();
        StringBuilder out = heading("Await lost stability");
        field(out, 0, "Expected", context.conditionDescription());
        optionalField(out, "Because", context.condition.explanation());
        field(out, 0, "Required", duration(context.configuration.stableForNanos()));
        field(out, 0, "Failure detected after", duration(
                attempt.completedNanos() - outcome.acquiredNanos()));
        out.append('\n').append("Observed:\n");
        field(out, 4, "Actual", context.actualValue());
        field(out, 4, "Mismatch", assertion == null
                ? attempt.mismatch() : assertion.mismatch());
        if (assertion != null) {
            out.append('\n');
            field(out, 0, "Cause", assertion.cause());
        }
        out.append('\n').append("Timing:\n");
        field(out, 4, "Acquired after", duration(
                outcome.acquiredNanos() - outcome.startedNanos()));
        field(out, 4, "Interval", duration(context.configuration.everyNanos()));
        return finish(out);
    }

    private static String uncontrolled(Context context,
            Uncontrolled<?> attempt) {
        boolean interrupted = attempt.cause() instanceof InterruptedException;
        String title = interrupted ? "Await was interrupted" : switch (
                attempt.origin()) {
            case SOURCE -> "Await source retrieval failed";
            case CONDITION -> "Await condition evaluation failed";
            case WAITING -> "Await execution was unhandled";
        };
        StringBuilder out = heading(title);
        field(out, 0, "Attempt", Long.toString(attempt.number()));
        if (interrupted) {
            field(out, 0, "Origin",
                    attempt.origin().name().toLowerCase(Locale.ROOT));
        }
        field(out, 0, "Condition", context.conditionDescription());
        if (attempt instanceof AfterObservation<?>) {
            field(out, 0, "Actual", context.actualValue());
        }
        optionalField(out, "Because", context.condition.explanation());
        field(out, 0, "Cause", context.causeDiagnostic());
        return finish(out);
    }

    private static void timeoutTiming(StringBuilder out, Context context,
            long startedNanos, long completedNanos, boolean attempts) {
        field(out, 4, "Waited up to", duration(
                context.configuration.upToNanos()));
        field(out, 4, "Elapsed", duration(
                completedNanos - startedNanos));
        if (attempts) {
            field(out, 4, "Attempts", Long.toString(
                    context.outcome.attempt().number()));
        }
        field(out, 4, "Interval", duration(
                context.configuration.everyNanos()));
    }

    private static void optionalField(StringBuilder out, String label,
            String value) {
        if (value != null) {
            field(out, 0, label, value);
        }
    }

    private static void field(StringBuilder out, int indent, String label,
            String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String prefix = " ".repeat(indent);
        if (!normalized.contains("\n")) {
            out.append(prefix).append(label).append(": ")
                    .append(normalized).append('\n');
            return;
        }
        out.append(prefix).append(label).append(":\n");
        String contentPrefix = " ".repeat(indent + 4);
        for (String line : normalized.split("\n", -1)) {
            if (!line.isEmpty()) {
                out.append(contentPrefix).append(line);
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

    private static String duration(long nanos) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < UNIT_NANOS.length; index++) {
            long count = nanos / UNIT_NANOS[index];
            nanos %= UNIT_NANOS[index];
            if (count == 0) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(count).append(' ').append(UNIT_NAMES[index]);
            if (count != 1) {
                result.append('s');
            }
        }
        return result.isEmpty() ? "0 nanoseconds" : result.toString();
    }

    private static String render(Object value) {
        try {
            if (value != null && value.getClass().isArray()) {
                String array = deepToString(new Object[] {value});
                return array.substring(1, array.length() - 1);
            }
            return String.valueOf(value);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return "<value unavailable: toString() threw "
                    + typeName(failure) + ">";
        }
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

    public static final class Context {

        private final WaitOutcome<?> outcome;
        private final RuntimeCondition<?, ?> condition;
        private final WaitConfiguration configuration;

        private boolean descriptionMaterialized;
        private String description;
        private boolean actualMaterialized;
        private String actual;

        private Context(WaitOutcome<?> outcome,
                RuntimeCondition<?, ?> condition,
                WaitConfiguration configuration) {
            this.outcome = requireNonNull(outcome);
            this.condition = requireNonNull(condition);
            this.configuration = configuration;
        }

        public String conditionDescription() {
            if (!descriptionMaterialized) {
                descriptionMaterialized = true;
                try {
                    String rendered = condition.description().get();
                    description = rendered == null || rendered.isBlank()
                            ? DESCRIPTION_UNAVAILABLE : rendered;
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    throw fatal;
                } catch (Throwable failure) {
                    description = DESCRIPTION_UNAVAILABLE;
                }
            }
            return description;
        }

        public String actualValue() {
            if (!actualMaterialized) {
                actualMaterialized = true;
                actual = render(switch (outcome.attempt()) {
                    case Satisfied<?> value -> value.actual();
                    case Unsatisfied<?> value -> value.actual();
                    case AfterObservation<?> value ->
                            value.actual();
                    case BeforeObservation<?> ignored ->
                            throw new IllegalArgumentException(
                                    "attempt has no observed actual");
                });
            }
            return actual;
        }

        private AssertionDiagnostic assertionDiagnostic() {
            return throwableDiagnostic(terminalCause(outcome),
                    "assertion did not pass");
        }

        private String causeDiagnostic() {
            return throwableDiagnostic(terminalCause(outcome), null).cause();
        }
    }

    private static AssertionDiagnostic throwableDiagnostic(Throwable failure,
            String fallback) {
        String type = typeName(failure);
        try {
            String message = failure.getMessage();
            return message == null || message.isBlank()
                    ? new AssertionDiagnostic(fallback, type)
                    : new AssertionDiagnostic(message, type + ": " + message);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable messageFailure) {
            return new AssertionDiagnostic(fallback,
                    type + ": <message unavailable: getMessage() threw "
                            + typeName(messageFailure) + ">");
        }
    }

    private record AssertionDiagnostic(String mismatch, String cause) {}

    static final class FormattingFailure extends RuntimeException {

        private final transient Context context;

        private FormattingFailure(Context context, Throwable cause) {
            super(null, cause, false, false);
            this.context = context;
        }
    }
}
