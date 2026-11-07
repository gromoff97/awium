package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitFailure.Reason;
import io.github.gromoff97.awium.result.ConditionHistory;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public interface AwaitHandler<OBSERVED> {

    default void onStart(WaitStarted start) throws Throwable {}

    default void onAttemptStart(AttemptStarted attempt) throws Throwable {}

    void onAttemptFinish(AttemptFinished<OBSERVED> attempt) throws Throwable;

    List<ConditionHistory<OBSERVED>> onFinish(WaitFinished finish) throws Throwable;

    record WaitStarted(Condition<?, ?> condition, Duration interval, Duration timeout, Duration persistence) {
        public WaitStarted {
            requireNonNull(condition, "condition must not be null");
            requireNonNull(interval, "interval must not be null");
            requireNonNull(timeout, "timeout must not be null");
            requireNonNull(persistence, "persistence must not be null");
        }
    }

    record AttemptStarted(long number, Duration elapsed) {
        public AttemptStarted {
            if (number < 1) throw new IllegalArgumentException("attempt number must be positive");
            requireNonNull(elapsed, "elapsed must not be null");
        }
    }

    record AttemptFinished<OBSERVED>(long number, Duration elapsed, Attempt<OBSERVED> attempt,
            int historySlot, Attempt<OBSERVED> historyAttempt) {
        public AttemptFinished {
            if (number < 1) throw new IllegalArgumentException("attempt number must be positive");
            if (historySlot < 1) throw new IllegalArgumentException("history slot must be positive");
            requireNonNull(elapsed, "elapsed must not be null");
            requireNonNull(attempt, "attempt must not be null");
            requireNonNull(historyAttempt, "history attempt must not be null");
        }
    }

    record WaitFinished(Duration elapsed, Optional<Reason> failureReason,
            Optional<List<Condition<?, ?>>> historyConditions) {
        public WaitFinished {
            requireNonNull(elapsed, "elapsed must not be null");
            requireNonNull(failureReason, "failure reason must not be null");
            historyConditions = requireNonNull(historyConditions, "history conditions must not be null")
                    .map(List::copyOf);
        }
    }
}
