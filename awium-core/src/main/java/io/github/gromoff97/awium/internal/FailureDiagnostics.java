package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.function.Function;

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
        var attempt = step.reportedAttempt();
        var cause = attempt.cause();
        var text = new DiagnosticText(cause);
        step.relatedCauses().forEach(text::retain);
        text.line(reason.title());
        text.field("", "Attempt", attemptNumber + " (" + step.phase() + ")");
        if (histories.stream().noneMatch(history -> history.condition() == condition)) {
            text.line("");
            text.condition("Condition", condition, "");
        }
        text.render(() -> {
            text.diagnostic(diagnostics.apply(attempt));
            return "";
        });
        timing(text, outcome, configuration);
        text.histories(histories);
        if (histories.stream().noneMatch(history -> history.attempts().get(attemptNumber) == attempt)) {
            text.line("");
            text.appendAttempt("Last attempt #" + attemptNumber, attempt);
        }
        return new AwaitFailure(reason, text.finish(""), cause, text.failures());
    }

    private static Reason failureReason(WaitCompletion outcome) {
        return switch (outcome) {
            case PersistenceFailure ignored -> PERSISTENCE_FAILED;
            case Uncontrolled failure -> failure.interrupted()
                    ? INTERRUPTED : switch (((Failure<?>) failure.step().reportedAttempt()).operation()) {
                        case WAITING -> WAIT_FAILED;
                        case SOURCE -> SOURCE_FAILED;
                        case CONDITION -> CONDITION_FAILED;
                    };
            case Timeout ignored -> TIMEOUT;
            case SatisfiedWait ignored -> throw new IllegalArgumentException("successful outcomes have no failure diagnostics");
        };
    }

    private static void timing(DiagnosticText text, WaitCompletion outcome, WaitConfiguration configuration) {
        var completedAfter = outcome.step().completedAfterNanos();
        var elapsed = outcome.elapsedNanos();
        text.line("");
        text.field("", "Timing", "elapsed " + unsignedDuration(elapsed)
                + "; timeout " + unsignedDuration(configuration.upToNanos())
                + "; interval " + unsignedDuration(configuration.everyNanos()));
        if (elapsed != completedAfter) text.field("", "Last completed after", unsignedDuration(completedAfter));
        if (outcome instanceof PersistenceFailure failure) {
            var acquiredAfter = failure.acquiredAfterNanos();
            text.field("", "Persistence", "failed after " + unsignedDuration(completedAfter - acquiredAfter)
                    + "; required " + unsignedDuration(configuration.persistenceNanos())
                    + "; acquired after " + unsignedDuration(acquiredAfter));
        }
    }
}
