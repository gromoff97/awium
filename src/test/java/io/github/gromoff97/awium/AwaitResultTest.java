package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.AwaitAttempt.Phase.ACQUISITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void successRetainsLegitimateNullAndCopiesHistory() {
        var attempt = new AwaitAttempt<String, String>(1, ACQUISITION,
                new AwaitAttempt.Outcome.Evaluated<>(new AwaitAttempt.Timing.AfterObservation(
                                Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO), null,
                        new ConditionResult.Satisfied<>(null, ConditionResult.Context.Plain.INSTANCE)));
        var mutable = new ArrayList<>(List.of(attempt));

        var result = new AwaitResult.Satisfied<>(mutable, 1, null);
        mutable.clear();

        assertEquals(List.of(attempt), result.attempts());
        assertEquals(1, result.totalAttempts());
        assertNull(result.result());
        assertThrows(UnsupportedOperationException.class, () -> result.attempts().clear());
    }

    @Test
    void failedResultRequiresStructuredFailure() {
        var failure = new AwaitFailure(AwaitFailure.Reason.SOURCE_FAILED, "failed",
                new IllegalStateException("failed"), List.of());
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

    @Test
    void dynamicExpectationsRejectDescriptionsThatCannotBeRendered() {
        assertThrows(NullPointerException.class, () -> new ConditionResult.Context.Expectation(null, null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionResult.Context.Expectation(" \n", null));
    }
}
