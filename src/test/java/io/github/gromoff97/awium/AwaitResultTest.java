package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.AttemptRecord.Phase.ACQUISITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void successRetainsLegitimateNullAndCopiesHistory() {
        var attempt = new AttemptRecord<String, String>(300, ACQUISITION,
                new AttemptRecord.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                diagnosticCondition("ready", null), null,
                new AttemptRecord.Outcome.Evaluated<>(null, new ConditionResult.Satisfied<>(null)));
        var mutable = new ArrayList<>(List.of(attempt));

        var result = new AwaitResult.Satisfied<>(mutable, null);
        mutable.clear();

        assertEquals(List.of(attempt), result.attempts());
        assertEquals(300, result.totalAttempts());
        assertNull(result.result());
        assertThrows(UnsupportedOperationException.class, () -> result.attempts().clear());
    }

    @Test
    void failedResultRequiresStructuredFailure() {
        var failure = new AwaitFailure(AwaitFailure.Reason.SOURCE_FAILED, "failed",
                new IllegalStateException("failed"), List.of());
        var result = new AwaitResult.Failed<String, String>(List.of(), failure);

        assertSame(failure, result.failure());
        assertEquals(0, result.totalAttempts());
        assertThrows(NullPointerException.class,
                () -> new AwaitResult.Failed<String, String>(List.of(), null));
    }

    @Test
    void attemptNumbersHaveConciseValidationMessages() {
        assertEquals("attempt number must be positive", assertThrows(
                IllegalArgumentException.class,
                () -> new AttemptRecord<>(0, null, null, null, null, null)).getMessage());
    }

    @Test
    void sequenceProgressRequiresAnExistingEvaluatedStage() {
        var condition = diagnosticCondition("first stage", null);
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(0, 2, 0, condition));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(0, 0, 1, condition));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(-1, 2, 1, condition));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(3, 2, 1, condition));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(1, 2, 3, condition));
        assertThrows(NullPointerException.class, () -> new AttemptRecord.Progress(0, 2, 1, null));
    }
}
