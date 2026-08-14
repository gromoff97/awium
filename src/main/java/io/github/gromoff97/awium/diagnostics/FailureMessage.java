package io.github.gromoff97.awium.diagnostics;

import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.engine.WaitOutcome;

import java.util.Locale;
import java.util.StringJoiner;

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
public final class FailureMessage {

    private static final String DESCRIPTION_UNAVAILABLE = "condition description unavailable";
    private static final long[] UNIT_NANOS = {86_400_000_000_000L, 3_600_000_000_000L, 60_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L, 1L};
    private static final String[] UNIT_NAMES = {"day", "hour", "minute", "second", "millisecond", "microsecond", "nanosecond"};

    private FailureMessage() {
        throw new AssertionError("Utility class");
    }

    static String format(WaitOutcome<?> outcome, RuntimeCondition<?, ?> condition, WaitConfiguration configuration) {
        Context context = new Context(outcome, condition, configuration);
        try {
            return switch (context.outcome) {
                case TimeoutBetweenObservations<?> value -> unsatisfied(context, value.attempt(), "Acquisition deadline elapsed before the next attempt");
                case LateUnsatisfiedTimeout<?> value -> unsatisfied(context, value.attempt(),
                        "Condition remained unsatisfied at or after the acquisition deadline");
                case LateSatisfiedTimeout<?> value -> lateSatisfied(context, value);
                case StabilityLoss<?> value -> unsatisfied(context, value.attempt(), "Condition did not remain stable for the required duration");
                case Uncontrolled<?> value -> uncontrolled(context, value);
                case Satisfied<?> ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
            };
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            throw new FormattingFailure(context, failure);
        }
    }

    public static String configurationConflict(long everyNanos, long upToNanos) {
        return "polling interval (" + duration(everyNanos) + ") must be shorter than acquisition timeout (" + duration(upToNanos) + ")";
    }

    static String emergency(FormattingFailure failure) {
        Context context = failure.context;
        StringBuilder out = heading("Failure diagnostics could not be formatted");
        condition(out, context.description == null ? DESCRIPTION_UNAVAILABLE : context.description, context.condition.explanation());
        String actual = context.outcome.attempt() instanceof BeforeObservation<?>
                ? null : context.actual == null
                        ? "<value unavailable: diagnostics failed>"
                        : context.actual;
        String mismatch = context.outcome.attempt() instanceof Unsatisfied<?> value
                ? value.mismatch() : null;
        attempt(out, context.outcome.attempt().number(), "diagnostics", actual, mismatch);
        timing(out, context);
        cause(out, emergencyDiagnostic(failure.getCause()));
        return finish(out);
    }

    private static String unsatisfied(Context context, Unsatisfied<?> attempt, String heading) {
        ThrowableDiagnostic assertion = attempt.assertionCause() == null
                ? null : context.causeDiagnostic();
        StringBuilder out = heading(heading);
        condition(out, context);
        attempt(out, attempt.number(), null, context.actualValue(), attempt.mismatch());
        timing(out, context);
        if (assertion != null) {
            cause(out, assertion);
        }
        return finish(out);
    }

    private static String lateSatisfied(Context context, LateSatisfiedTimeout<?> outcome) {
        StringBuilder out = heading("Condition became satisfied at or after the acquisition deadline");
        condition(out, context);
        attempt(out, outcome.attempt().number(), null, context.actualValue(), null);
        timing(out, context);
        return finish(out);
    }

    private static String uncontrolled(Context context, Uncontrolled<?> attempt) {
        String title = attempt.cause() instanceof InterruptedException
                ? "Caller thread was interrupted"
                : switch (attempt.origin()) {
            case SOURCE -> "Source retrieval failed";
            case CONDITION -> "Condition evaluation failed";
            case WAITING -> "Waiting before the next attempt failed";
        };
        StringBuilder out = heading(title);
        condition(out, context);
        String actual = attempt instanceof AfterObservation<?>
                ? context.actualValue() : null;
        attempt(out, attempt.number(), attempt.origin().name().toLowerCase(Locale.ROOT), actual, null);
        timing(out, context);
        cause(out, context.causeDiagnostic());
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
        condition(out, context.conditionDescription(), context.condition.explanation());
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

    private static String duration(long nanos) {
        StringJoiner result = new StringJoiner(" ");
        for (int index = 0; index < UNIT_NANOS.length; index++) {
            long count = nanos / UNIT_NANOS[index];
            nanos %= UNIT_NANOS[index];
            if (count == 0) {
                continue;
            }
            result.add(count + " " + UNIT_NAMES[index] + (count == 1 ? "" : "s"));
        }
        return result.length() == 0 ? "0 nanoseconds" : result.toString();
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

    private static final class Context {

        private final WaitOutcome<?> outcome;
        private final RuntimeCondition<?, ?> condition;
        private final WaitConfiguration configuration;

        private String description;
        private String actual;

        private Context(WaitOutcome<?> outcome, RuntimeCondition<?, ?> condition, WaitConfiguration configuration) {
            this.outcome = requireNonNull(outcome, "outcome must not be null");
            this.condition = requireNonNull(condition, "condition must not be null");
            this.configuration = requireNonNull(configuration, "configuration must not be null");
        }

        private String conditionDescription() {
            String rendered = requireNonNull(condition.description().get(), "condition description must not be null");
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

    static final class FormattingFailure extends RuntimeException {

        private final transient Context context;

        private FormattingFailure(Context context, Throwable cause) {
            super(null, cause, false, false);
            this.context = context;
        }

        Throwable engineCause() {
            return terminalCause(context.outcome);
        }
    }
}
