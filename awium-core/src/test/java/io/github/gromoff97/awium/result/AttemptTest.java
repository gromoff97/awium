package io.github.gromoff97.awium.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttemptTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void unsatisfiedObservationsRequireANonBlankMessage(String message) {
        var failure = assertThrows(IllegalArgumentException.class,
                () -> new UnsatisfiedObservation<>("actual", message, null));
        assertEquals("unsatisfied message must not be null or blank", failure.getMessage());
    }

    @Test
    void failuresRequireAnOperationAndACause() {
        var cause = new IllegalStateException("failed");
        assertThrows(NullPointerException.class, () -> new Attempt.FailedAfterObservation<>(null, null, cause, null));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedBeforeObservation<>(null, cause));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedAfterObservation<>(null, Attempt.Operation.CONDITION, null, null));
        assertThrows(NullPointerException.class, () -> new Attempt.FailedBeforeObservation<>(Attempt.Operation.SOURCE, null));
    }
}
