package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.util.List;

import static io.github.gromoff97.awium.execution.DiagnosticText.field;
import static io.github.gromoff97.awium.execution.DiagnosticText.unsignedDuration;
import static io.github.gromoff97.awium.execution.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.execution.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.execution.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.execution.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.PERSISTENCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static java.util.Objects.requireNonNull;

final class FailureDiagnostics {

    private FailureDiagnostics() {}

    static AwaitFailure failure(AbstractConditionRun<?, ?> condition, WaitCompletion outcome,
            WaitTiming configuration, long attemptNumber, List<? extends ConditionHistory<?>> histories) {
        requireNonNull(outcome, "outcome must not be null");
        requireNonNull(configuration, "configuration must not be null");
        var reason = failureReason(outcome);
        var step = outcome.step();
        var attempt = step.attempt();
        var cause = attempt.cause();
        var text = new DiagnosticText(cause);
        var out = new StringBuilder(title(reason, attempt)).append('\n');
        field(out, "", "Attempt", attemptNumber + " (" + step.phase() + ")");
        out.append('\n');
        text.condition(out, "Condition", condition.condition, "");
        text.render(() -> {
            text.diagnostic(out, condition.diagnostics(attempt));
            return "";
        });
        text.attemptDetails(out, attempt);
        timing(out, outcome, configuration);
        mismatchHistory(out, histories);
        if (cause != null) field(out, "", "Cause", text.cause(cause));
        text.appendFailures(out, "");
        return new AwaitFailure(reason, out.substring(0, out.length() - 1), cause, text.failures());
    }

    private static void mismatchHistory(StringBuilder out, List<? extends ConditionHistory<?>> histories) {
        for (int i = 0; i < histories.size(); i++) {
            var mismatches = histories.get(i).attempts().entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof UnsatisfiedObservation<?>).toList();
            if (mismatches.isEmpty()) continue;
            out.append("\nUnsatisfied attempts (condition ").append(i + 1).append("):\n");
            for (var entry : mismatches) {
                field(out, "Through #" + entry.getKey(), entry.getValue().diagnostic().mismatch());
            }
        }
    }

    private static Reason failureReason(WaitCompletion outcome) {
        return switch (outcome) {
            case PersistenceFailure ignored -> PERSISTENCE_FAILED;
            case Uncontrolled failure -> failure.interrupted()
                    ? INTERRUPTED : switch (((Failure<?>) failure.step().attempt()).operation()) {
                        case WAITING -> WAIT_FAILED;
                        case SOURCE -> SOURCE_FAILED;
                        case CONDITION -> CONDITION_FAILED;
                    };
            case Timeout ignored -> TIMEOUT;
            case SatisfiedWait ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private static String title(Reason reason, Attempt<?> attempt) {
        return switch (reason) {
            case TIMEOUT -> "Wait timed out";
            case PERSISTENCE_FAILED -> "Condition did not stay satisfied";
            case SOURCE_FAILED, CONDITION_FAILED, WAIT_FAILED, INTERRUPTED -> (switch (((Failure<?>) attempt).operation()) {
                case WAITING -> "Waiting";
                case CONDITION -> "Condition evaluation";
                case SOURCE -> "Source retrieval";
            }) + (reason == INTERRUPTED ? " interrupted" : " failed");
        };
    }

    private static void timing(StringBuilder out, WaitCompletion outcome, WaitTiming configuration) {
        var completedAfter = outcome.step().completedAfterNanos();
        var elapsed = outcome instanceof Timeout timeout ? timeout.elapsedNanos() : completedAfter;
        out.append('\n');
        field(out, "", "Timing", "elapsed " + unsignedDuration(elapsed)
                + "; timeout " + unsignedDuration(configuration.upToNanos())
                + "; interval " + unsignedDuration(configuration.everyNanos()));
        if (elapsed != completedAfter) field(out, "", "Last completed after", unsignedDuration(completedAfter));
        if (outcome instanceof PersistenceFailure failure) {
            var acquiredAfter = failure.acquiredAfterNanos();
            field(out, "", "Persistence", "failed after " + unsignedDuration(completedAfter - acquiredAfter)
                    + "; required " + unsignedDuration(configuration.persistenceNanos())
                    + "; acquired after " + unsignedDuration(acquiredAfter));
        }
    }
}
