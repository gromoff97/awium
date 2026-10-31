package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;

import static java.util.Objects.requireNonNull;
import static io.github.gromoff97.awium.internal.DiagnosticText.unsignedDuration;
import static io.github.gromoff97.awium.internal.DiagnosticText.field;

public final class FailureDiagnostics {

    private FailureDiagnostics() {}

    public static AwaitFailure failure(WaitCompletion outcome, WaitConfiguration configuration, long attemptNumber) {
        requireNonNull(outcome, "outcome must not be null");
        requireNonNull(configuration, "configuration must not be null");
        var reason = outcome.failureReason();
        var step = outcome.step();
        var attempt = step.attempt();
        var cause = attempt.cause();
        var text = new DiagnosticText(cause);
        var sequence = step.definition() instanceof Condition.Captured captured ? captured : null;
        var out = new StringBuilder(title(outcome)).append('\n');
        field(out, "", "Attempt", attemptNumber + " (" + step.phase() + ")");
        if (sequence != null) {
            field(out, "", "Sequence", "captured " + step.completedStages() + "/" + sequence.stages().size()
                    + "; stage " + step.stageNumber() + "/" + sequence.stages().size());
            if (sequence.explanation() != null) field(out, "Because", sequence.explanation());
        }
        out.append('\n');
        text.condition(out, "Condition", step.condition(), "");
        String actual = text.observation(attempt);
        if (actual != null) field(out, "Actual", actual);
        if (attempt instanceof Attempt.Unsatisfied<?> mismatch && mismatch.mismatch() != null) {
            field(out, "Mismatch", mismatch.mismatch());
        }
        if (attempt instanceof Attempt.Failure<?> failure && failure.location() != null) {
            field(out, "Within", failure.location());
        }
        if (sequence != null && attempt instanceof Attempt.Satisfied<?> && step.completedStages() < sequence.stages().size()) {
            int next = step.completedStages() + 1;
            text.condition(out, "Next stage " + next + "/" + sequence.stages().size(), sequence.stages().get(next - 1), "");
        }
        timing(out, outcome, configuration);
        if (cause != null) field(out, "", "Cause", text.cause(cause));
        text.appendFailures(out, "");
        return new AwaitFailure(reason, out.substring(0, out.length() - 1), cause, text.failures());
    }

    private static String title(WaitCompletion outcome) {
        return switch (outcome) {
            case WaitCompletion.Timeout ignored -> "Wait timed out";
            case WaitCompletion.PersistenceFailure ignored -> "Condition did not stay satisfied";
            case WaitCompletion.Uncontrolled failure -> (switch (((Attempt.Failure<?>) failure.step().attempt()).operation()) {
                case WAITING -> "Waiting";
                case CONDITION -> "Condition evaluation";
                case SOURCE -> "Source retrieval";
            }) + (failure.interrupted() ? " interrupted" : " failed");
            case WaitCompletion.Satisfied ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private static void timing(StringBuilder out, WaitCompletion outcome, WaitConfiguration configuration) {
        var completedAfter = outcome.step().completedAfterNanos();
        var elapsed = outcome instanceof WaitCompletion.Timeout timeout ? timeout.elapsedNanos() : completedAfter;
        out.append('\n');
        field(out, "", "Timing", "elapsed " + unsignedDuration(elapsed)
                + "; timeout " + unsignedDuration(configuration.upToNanos())
                + "; interval " + unsignedDuration(configuration.everyNanos()));
        if (elapsed != completedAfter) field(out, "", "Last completed after", unsignedDuration(completedAfter));
        if (outcome instanceof WaitCompletion.PersistenceFailure failure) {
            var acquiredAfter = failure.acquiredAfterNanos();
            field(out, "", "Persistence", "failed after " + unsignedDuration(completedAfter - acquiredAfter)
                    + "; required " + unsignedDuration(configuration.persistenceNanos())
                    + "; acquired after " + unsignedDuration(acquiredAfter));
        }
    }
}
