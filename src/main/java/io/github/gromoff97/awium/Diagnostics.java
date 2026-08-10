package io.github.gromoff97.awium;

final class Diagnostics implements DiagnosticFormatter {

    @Override
    public String format(FailureContext<?> context) {
        return switch (context.outcome().kind()) {
            case TIMEOUT_BETWEEN_OBSERVATIONS -> timeoutBetween(context);
            case LATE_UNSATISFIED_TIMEOUT -> lateUnsatisfied(context);
            case LATE_SATISFIED_TIMEOUT -> lateSatisfied(context);
            case STABILITY_LOSS -> stabilityLoss(context);
            case UNCONTROLLED -> uncontrolled(context);
            case SUCCESS -> throw new IllegalArgumentException(
                    "successful outcomes have no failure diagnostics");
        };
    }

    private static String timeoutBetween(FailureContext<?> context) {
        WaitOutcome<?> outcome = context.outcome();
        WaitOutcome.LastObservation last = outcome.lastObservation();
        FailureContext.AssertionDiagnostic assertion = last.assertionCause() == null
                ? null : context.assertionDiagnostic("assertion did not pass");
        StringBuilder out = heading("Await timed out");
        field(out, 0, "Condition", context.conditionDescription());
        field(out, 0, "Reason",
                "acquisition deadline elapsed before the next observation");
        out.append('\n').append("Last observation:\n");
        field(out, 4, "Attempt", Long.toString(last.attempt()));
        field(out, 4, "Completed after", duration(
                last.completedNanos() - outcome.startedNanos()));
        field(out, 4, "Mismatch", assertion == null
                ? last.mismatch() : assertion.mismatch());
        if (assertion != null) {
            out.append('\n');
            field(out, 0, "Cause", assertion.cause());
        }
        out.append('\n').append("Timing:\n");
        timeoutTiming(out, context, false);
        return finish(out);
    }

    private static String lateUnsatisfied(FailureContext<?> context) {
        ObservationOutcome<?> observation = context.outcome().observation();
        FailureContext.AssertionDiagnostic assertion =
                observation.assertionCause() == null ? null
                        : context.assertionDiagnostic("assertion did not pass");
        StringBuilder out = heading("Await timed out");
        field(out, 0, "Condition", context.conditionDescription());
        field(out, 0, "Observed", context.actualValue());
        field(out, 0, "Mismatch", assertion == null
                ? observation.mismatch() : assertion.mismatch());
        optionalField(out, "Because", context.explanation());
        if (assertion != null) {
            field(out, 0, "Cause", assertion.cause());
        }
        out.append('\n').append("Timing:\n");
        timeoutTiming(out, context, true);
        return finish(out);
    }

    private static String lateSatisfied(FailureContext<?> context) {
        StringBuilder out = heading("Await timed out");
        field(out, 0, "Condition", context.conditionDescription());
        field(out, 0, "Observed", context.actualValue());
        field(out, 0, "Reason", "condition became satisfied after the timeout");
        optionalField(out, "Because", context.explanation());
        out.append('\n').append("Timing:\n");
        timeoutTiming(out, context, true);
        return finish(out);
    }

    private static String stabilityLoss(FailureContext<?> context) {
        WaitOutcome<?> outcome = context.outcome();
        ObservationOutcome<?> observation = outcome.observation();
        FailureContext.AssertionDiagnostic assertion =
                observation.assertionCause() == null ? null
                        : context.assertionDiagnostic("assertion did not pass");
        StringBuilder out = heading("Await lost stability");
        field(out, 0, "Expected", context.conditionDescription());
        optionalField(out, "Because", context.explanation());
        field(out, 0, "Required", duration(context.config().stableForNanos()));
        field(out, 0, "Failure detected after", duration(
                outcome.completedNanos() - outcome.acquiredNanos()));
        out.append('\n').append("Observed:\n");
        field(out, 4, "Actual", context.actualValue());
        field(out, 4, "Mismatch", assertion == null
                ? observation.mismatch() : assertion.mismatch());
        if (assertion != null) {
            out.append('\n');
            field(out, 0, "Cause", assertion.cause());
        }
        out.append('\n').append("Timing:\n");
        field(out, 4, "Acquired after", duration(
                outcome.acquiredNanos() - outcome.startedNanos()));
        field(out, 4, "Interval", duration(context.config().everyNanos()));
        return finish(out);
    }

    private static String uncontrolled(FailureContext<?> context) {
        ObservationOutcome<?> observation = context.outcome().observation();
        boolean interrupted = observation.cause() instanceof InterruptedException;
        String heading = interrupted ? "Await was interrupted" : switch (
                observation.origin()) {
            case SOURCE -> "Await source retrieval failed";
            case CONDITION -> "Await condition evaluation failed";
            case WAITING -> "Await execution was unhandled";
        };
        StringBuilder out = heading(heading);
        field(out, 0, "Attempt", Long.toString(observation.attempt()));
        if (interrupted) {
            field(out, 0, "Origin", origin(observation.origin()));
        }
        field(out, 0, "Condition", context.conditionDescription());
        if (context.hasActual()) {
            field(out, 0, "Actual", context.actualValue());
        }
        optionalField(out, "Because", context.explanation());
        field(out, 0, "Cause", context.causeDiagnostic());
        return finish(out);
    }

    private static void timeoutTiming(StringBuilder out,
            FailureContext<?> context, boolean attempts) {
        WaitOutcome<?> outcome = context.outcome();
        field(out, 4, "Waited up to", duration(context.config().upToNanos()));
        field(out, 4, "Elapsed", duration(
                outcome.completedNanos() - outcome.startedNanos()));
        if (attempts) {
            field(out, 4, "Attempts", Long.toString(outcome.completedAttempts()));
        }
        field(out, 4, "Interval", duration(context.config().everyNanos()));
    }

    private static void optionalField(StringBuilder out, String label,
            String value) {
        if (value != null) {
            field(out, 0, label, value);
        }
    }

    static void field(StringBuilder out, int indent, String label, String value) {
        String normalized = normalizeNewlines(value);
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

    static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static StringBuilder heading(String value) {
        return new StringBuilder(value).append("\n\n");
    }

    private static String finish(StringBuilder out) {
        out.setLength(out.length() - 1);
        return out.toString();
    }

    private static String duration(long nanos) {
        return DurationFormatter.format(nanos);
    }

    private static String origin(ObservationOutcome.Origin origin) {
        return switch (origin) {
            case WAITING -> "waiting";
            case SOURCE -> "source";
            case CONDITION -> "condition";
        };
    }
}
