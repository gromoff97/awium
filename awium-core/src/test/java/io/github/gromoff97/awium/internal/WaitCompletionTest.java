package io.github.gromoff97.awium.internal;

import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.PERSISTENCE;
import static io.github.gromoff97.awium.internal.WaitCompletion.PersistenceFailure;
import static io.github.gromoff97.awium.internal.WaitCompletion.SatisfiedWait;
import static io.github.gromoff97.awium.internal.WaitCompletion.Timeout;
import static io.github.gromoff97.awium.internal.WaitCompletion.Uncontrolled;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaitCompletionTest {
    @Test
    void persistenceCallbacksRejectNullForEveryOutcome() {
        var successful = new ExecutionStep<>(ACQUISITION, 1, new SatisfiedObservation<>("actual"), true);
        var mismatch = new ExecutionStep<>(PERSISTENCE, 2, new UnsatisfiedObservation<>("actual", "pending", null), false);
        var failed = mismatch.withConditionFailure(new IllegalStateException("probe failed"), null);
        List<WaitCompletion> outcomes = List.of(new SatisfiedWait(successful), new Timeout(mismatch),
                new PersistenceFailure(0, mismatch), new Uncontrolled(failed, false));

        for (var outcome : outcomes) {
            assertThrows(NullPointerException.class, () -> outcome.onPersistenceFailure(null), outcome.getClass().getSimpleName());
        }
    }
}
