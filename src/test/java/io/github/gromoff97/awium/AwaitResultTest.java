package io.github.gromoff97.awium;

import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.results.AwaitAttempt.Phase.ACQUISITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void successRetainsLegitimateNullAndCopiesHistory() {
        var attempt = new AwaitAttempt<String, String>(1, ACQUISITION,
                new AwaitAttempt.Outcome.Satisfied<>(
                        new AwaitAttempt.Timing.AfterObservation(
                                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                        null, null));
        var mutable = new ArrayList<>(List.of(attempt));

        var result = new AwaitResult.Satisfied<>(mutable, 1, null);
        mutable.clear();

        assertEquals(List.of(attempt), result.attempts());
        assertEquals(1, result.totalAttempts());
        assertNull(result.result());
        assertThrows(UnsupportedOperationException.class, () -> result.attempts().clear());
    }

    @Test
    void failureRequiresItsCauseAndCopiesHistory() {
        var failure = new IllegalStateException("failed");
        var result = new AwaitResult.Failed<String, String>(List.of(), 0, failure);

        assertSame(failure, result.failure());
        assertThrows(NullPointerException.class,
                () -> new AwaitResult.Failed<String, String>(List.of(), 0, null));
    }

    @Test
    void attemptAndResultCountsHaveConciseValidationMessages() {
        assertEquals("attempt number must be positive", assertThrows(
                IllegalArgumentException.class,
                () -> new AwaitAttempt<>(0, null, null)).getMessage());
        assertEquals("total attempts must be non-negative", assertThrows(
                IllegalArgumentException.class,
                () -> new AwaitResult.Satisfied<>(List.of(), -1, null)).getMessage());
    }
}
