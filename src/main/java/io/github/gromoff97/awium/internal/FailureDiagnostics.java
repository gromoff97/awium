package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.Attempt;

import static io.github.gromoff97.awium.Condition.Captured;
import static io.github.gromoff97.awium.internal.DiagnosticText.field;
import static io.github.gromoff97.awium.internal.DiagnosticText.unsignedDuration;
import static io.github.gromoff97.awium.internal.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.PERSISTENCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;
import static java.util.Objects.requireNonNull;

public final class FailureDiagnostics {

    private FailureDiagnostics() {}

    public static AwaitFailure failure(Condition condition, WaitCompletion outcome,
            WaitConfiguration configuration, long attemptNumber) {
        requireNonNull(outcome, "outcome must not be null");
        requireNonNull(configuration, "configuration must not be null");
        var reason = failureReason(outcome);
        var step = outcome.step();
        var attempt = step.attempt();
        var cause = attempt.cause();
        var text = new DiagnosticText(cause);
        var sequence = condition instanceof Captured<?> captured ? captured : null;
        var out = new StringBuilder(title(reason, attempt)).append('\n');
        field(out, "", "Attempt", attemptNumber + " (" + step.phase() + ")");
        if (sequence != null) {
            field(out, "", "Sequence", "captured " + step.completedStages() + "/" + sequence.stages().size()
                    + "; stage " + step.stageNumber() + "/" + sequence.stages().size());
            if (sequence.explanation() != null) field(out, "Because", sequence.explanation());
        }
        out.append('\n');
        text.condition(out, "Condition", sequence == null ? condition : sequence.stages().get(step.stageNumber() - 1), "");
        String actual = text.observation(attempt);
        if (actual != null) field(out, "Actual", actual);
        if (attempt instanceof UnsatisfiedObservation<?> mismatch && mismatch.mismatch() != null) {
            field(out, "Mismatch", mismatch.mismatch());
        }
        if (attempt instanceof Failure<?> failure && failure.location() != null) {
            field(out, "Within", failure.location());
        }
        if (sequence != null && attempt instanceof SatisfiedObservation<?> && step.completedStages() < sequence.stages().size()) {
            int next = step.completedStages() + 1;
            text.condition(out, "Next stage " + next + "/" + sequence.stages().size(), sequence.stages().get(next - 1), "");
        }
        timing(out, outcome, configuration);
        if (cause != null) field(out, "", "Cause", text.cause(cause));
        text.appendFailures(out, "");
        return new AwaitFailure(reason, out.substring(0, out.length() - 1), cause, text.failures());
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

    private static void timing(StringBuilder out, WaitCompletion outcome, WaitConfiguration configuration) {
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
