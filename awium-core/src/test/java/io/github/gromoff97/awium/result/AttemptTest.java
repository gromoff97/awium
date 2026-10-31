package io.github.gromoff97.awium.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AttemptTest {
    @Test
    void failuresRequireAnOperationAndACause() {
        var cause = new IllegalStateException("failed");
        assertThrows(NullPointerException.class, () -> new Attempt.FailedAfterObservation<>(null, null, cause, null));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedBeforeObservation<>(null, cause));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedAfterObservation<>(null, Attempt.Operation.CONDITION, null, null));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedBeforeObservation<>(Attempt.Operation.SOURCE, null));
    }
}
