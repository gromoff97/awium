package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.internal.DiagnosticText.field;
import static io.github.gromoff97.awium.internal.DiagnosticText.unsignedDuration;
import static io.github.gromoff97.awium.internal.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.INTERRUPTED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.PERSISTENCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.SOURCE_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.WAIT_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason;

final class FailureDiagnostics {

    private FailureDiagnostics() {}

    static AwaitFailure failure(AwaitCondition condition, Function<Attempt<?>, Diagnostic> diagnostics,
            WaitCompletion outcome, WaitConfiguration configuration, long attemptNumber, List<? extends ConditionHistory<?>> histories) {
        var reason = failureReason(outcome);
        var step = outcome.step();
        var attempt = step.attempt();
        var cause = attempt.cause();
        var text = new DiagnosticText(cause);
        step.relatedCauses().forEach(text::retain);
        var out = new StringBuilder(title(reason, attempt)).append('\n');
        field(out, "", "Attempt", attemptNumber + " (" + step.phase() + ")");
        if (histories.stream().noneMatch(history -> history.condition() == condition)) {
            out.append('\n');
            text.condition(out, "Condition", condition, "");
        }
        text.render(() -> {
            text.diagnostic(out, diagnostics.apply(attempt));
            return "";
        });
        timing(out, outcome, configuration);
        text.histories(out, histories);
        if (histories.stream().noneMatch(history -> history.attempts().get(attemptNumber) == attempt)) {
            out.append('\n');
            text.appendAttempt(out, "Last attempt #" + attemptNumber, attempt);
        }
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
