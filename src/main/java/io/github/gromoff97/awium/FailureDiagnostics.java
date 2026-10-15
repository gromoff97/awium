package io.github.gromoff97.awium;

import io.github.gromoff97.awium.ConditionResult.Reference;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static io.github.gromoff97.awium.ValueRendering.preservingInterrupt;
import static io.github.gromoff97.awium.ValueRendering.renderValue;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class FailureDiagnostics {

    private static final List<TimeUnit> DURATION_UNITS = List.of(
            TimeUnit.DAYS, TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS,
            TimeUnit.MILLISECONDS, TimeUnit.MICROSECONDS, TimeUnit.NANOSECONDS);

    private static final int CAUSE_MESSAGE_LIMIT = 160;
    private static final String STACK_TRACE_HINT = "… <see stack trace>";

    private final WaitCompletion<?, ?> outcome;
    private final ConditionMetadata metadata;
    private final WaitConfiguration configuration;
    private final Object observed;
    private final String mismatch;
    private final AwaitFailure.Reason reason;
    private final Throwable cause;
    private final ConditionResult.Context.Sequence sequence;

    private String actual;
    private String referenceValue;
    private String sequenceReference;

    private FailureDiagnostics(WaitCompletion<?, ?> outcome,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        this.outcome = requireNonNull(outcome, "outcome must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        requireNonNull(metadata, "metadata must not be null");
        var attempt = outcome.attempt().outcome();
        var evaluation = attempt instanceof AwaitAttempt.Outcome.Evaluated<?, ?> value ? value.evaluation() : null;
        observed = switch (attempt) {
            case AwaitAttempt.Outcome.Evaluated<?, ?> value -> value.observed();
            case AwaitAttempt.Outcome.SourceInterrupted<?, ?> value -> value.observed();
            default -> null;
        };
        mismatch = evaluation instanceof ConditionResult.Unsatisfied<?> value ? value.mismatch() : null;
        reason = outcome.failureReason();
        cause = outcome.cause();
        var context = evaluation == null ? null : evaluation.context();
        sequence = context instanceof ConditionResult.Context.Sequence value ? value : null;
        this.metadata = context instanceof ConditionResult.Context.Expectation expectation
                ? new ConditionMetadata(expectation.description(), metadata.explanation(), expectation.reference())
                : metadata;
    }

    static String duration(long nanos) {
        StringJoiner result = new StringJoiner(" ");
        for (TimeUnit unit : DURATION_UNITS) {
            long unitNanos = unit.toNanos(1);
            long count = nanos / unitNanos;
            nanos %= unitNanos;
            if (count != 0) {
                String name = unit.name().toLowerCase(Locale.ROOT);
                result.add(count + " " + (count == 1 ? name.substring(0, name.length() - 1) : name));
            }
        }
        return result.length() == 0 ? "0 nanoseconds" : result.toString();
    }

    static AwaitFailure failure(WaitCompletion<?, ?> outcome, ConditionMetadata metadata, WaitConfiguration configuration) {
        return new FailureDiagnostics(outcome, metadata, configuration).failure();
    }

    private AwaitFailure failure() {
        if (cause instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        boolean restoreInterrupt = currentThread().isInterrupted() || cause instanceof InterruptedException;
        try {
            String message;
            try {
                message = message(title(), null);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                addSuppressed(fatal, cause);
                throw fatal;
            } catch (Throwable failure) {
                restoreInterrupt |= failure instanceof InterruptedException;
                try {
                    message = message(title(), failure);
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    addSuppressed(fatal, failure);
                    if (cause != failure) {
                        addSuppressed(fatal, cause);
                    }
                    throw fatal;
                }
                return new AwaitFailure(reason, message, cause,
                        cause == failure ? List.of() : List.of(failure));
            }
            return new AwaitFailure(reason, message, cause, List.of());
        } finally {
            if (restoreInterrupt) {
                currentThread().interrupt();
            }
        }
    }

    private String uncontrolledHeading() {
        boolean interrupted = cause instanceof InterruptedException;
        return switch (outcome.attempt().outcome()) {
            case AwaitAttempt.Outcome.WaitingFailed<?, ?> ignored -> interrupted
                    ? "Caller thread was interrupted while waiting" : "Waiting before the next attempt failed";
            case AwaitAttempt.Outcome.Evaluated<?, ?> ignored -> interrupted
                    ? "Caller thread was interrupted during condition evaluation" : "Condition evaluation failed";
            default -> interrupted
                    ? "Caller thread was interrupted during source retrieval" : "Source retrieval failed";
        };
    }

    private static void addSuppressed(Throwable failure, Throwable cause) {
        if (cause != null && cause != failure) {
            failure.addSuppressed(cause);
        }
    }

    private String title() {
        return switch (outcome) {
            case WaitCompletion.TimeoutBetweenObservations<?, ?> ignored ->
                    "Acquisition deadline elapsed before the next attempt";
            case WaitCompletion.LateTimeout<?, ?> value ->
                    value.attempt().outcome() instanceof AwaitAttempt.Outcome.Evaluated<?, ?> evaluated
                            && evaluated.evaluation() instanceof ConditionResult.Satisfied<?>
                            ? "Condition became satisfied at or after the acquisition deadline"
                            : "Condition remained unsatisfied at or after the acquisition deadline";
            case WaitCompletion.PersistenceFailure<?, ?> ignored ->
                    "Condition did not persist for the required duration";
            case WaitCompletion.Uncontrolled<?, ?> ignored ->
                    uncontrolledHeading();
            case WaitCompletion.Satisfied<?, ?> ignored ->
                    throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private String message(String title, Throwable diagnosticFailure) {
        boolean emergency = diagnosticFailure != null;
        AwaitAttempt<?, ?> attempt = outcome.attempt();
        StringBuilder out = new StringBuilder(title).append("\n\n");
        condition(out, emergency);
        String renderedActual = null;
        if (attempt.outcome().timing() instanceof AwaitAttempt.Timing.AfterObservation) {
            if (!emergency) {
                actual = renderValue(observed);
            }
            renderedActual = actual == null ? "<value unavailable: diagnostics failed>" : actual;
        }
        attempt(out, attempt.number(), renderedActual,
                sequence == null ? mismatch : null);
        sequence(out, emergency);
        if (emergency || !(outcome instanceof WaitCompletion.Uncontrolled<?, ?>)) {
            timing(out);
        }
        Throwable failure = emergency ? diagnosticFailure : cause;
        if (failure != null) {
            cause(out, emergency ? "Diagnostics failure" : "Cause",
                    emergency ? emergencyDiagnostic(failure) : throwableDiagnostic(failure));
        }
        return out.deleteCharAt(out.length() - 1).toString();
    }

    private void timing(StringBuilder out) {
        out.append('\n').append("Timing:\n");
        field(out, "Acquisition timeout",
                duration(configuration.upToNanos()));
        long completedAfter = outcome.attempt().outcome().timing().completionOffset().toNanos();
        switch (outcome) {
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
                        duration(configuration.persistenceNanos()));
                field(out, "Failure detected after", duration(completedAfter - acquiredAfter));
            }
            case WaitCompletion.Uncontrolled<?, ?> ignored -> {}
            case WaitCompletion.Satisfied<?, ?> ignored -> {}
        }
        field(out, "Polling interval",
                duration(configuration.everyNanos()));
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

    private static void cause(StringBuilder out, String label, ThrowableDiagnostic cause) {
        out.append('\n');
        field(out, "", label, cause.type());
        if (cause.message() != null) {
            field(out, "Message", cause.message());
        }
    }

    private void condition(StringBuilder out, boolean emergency) {
        field(out, "", "Condition", metadata.description());
        referenceValue = reference(out, metadata.reference(), referenceValue, emergency);
        if (metadata.explanation() != null) {
            field(out, "Importance", metadata.explanation());
        }
    }

    private void sequence(StringBuilder out, boolean emergency) {
        if (sequence == null) {
            return;
        }
        out.append('\n').append("Sequence (captured ").append(sequence.capturedStages())
                .append(" of ").append(sequence.totalStages()).append("):\n");
        field(out, "Expectation", sequence.expectation());
        sequenceReference = reference(out, sequence.reference(), sequenceReference, emergency);
        if (sequence.importance() != null) {
            field(out, "Importance", sequence.importance());
        }
        if (mismatch != null) {
            field(out, "Mismatch", mismatch);
        }
    }

    private static String reference(StringBuilder out, Reference<?> reference,
            String cached, boolean emergency) {
        if (reference == null) {
            return cached;
        }
        String rendered = emergency ? cached : renderValue(reference.value());
        field(out, reference.label(), rendered == null ? "<value unavailable: diagnostics failed>" : rendered);
        return rendered;
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

    private static String typeName(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
    }

    private static ThrowableDiagnostic throwableDiagnostic(Throwable failure) {
        return new ThrowableDiagnostic(typeName(failure), causeMessage(failure));
    }

    private static String causeMessage(Throwable failure) {
        String message = preservingInterrupt(failure::getMessage);
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
            if (ignored instanceof InterruptedException) currentThread().interrupt();
            return new ThrowableDiagnostic(typeName(failure), null);
        }
    }

    private record ThrowableDiagnostic(String type, String message) {}
}
