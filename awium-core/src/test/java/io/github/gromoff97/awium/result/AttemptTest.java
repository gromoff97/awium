package io.github.gromoff97.awium.result;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.Observation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttemptTest {
    @Test
    void aMismatchCauseDoesNotMarkAnExecutionFailure() {
        Attempt<String> attempt = new UnsatisfiedObservation<>("actual", "pending", new IllegalStateException("retry"));

        assertNull(attempt.operation());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "actual")
    void observedOutcomesExposeTheTypedSourceValue(String actual) {
        List<Observation<String>> observations = List.of(
                new SatisfiedObservation<>(actual),
                new UnsatisfiedObservation<>(actual, "pending", null),
                new FailedAfterObservation<>(actual, CONDITION, new IllegalStateException("probe failed"), null));

        for (var observation : observations) assertSame(actual, observation.observed());
    }

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
