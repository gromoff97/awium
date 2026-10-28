package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.result.AttemptRecord.Phase.ACQUISITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void successRetainsLegitimateNullAndCopiesHistory() {
        var attempt = new AttemptRecord<String>(300, ACQUISITION,
                new AttemptRecord.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                diagnosticCondition("ready", null), null,
                null, new AttemptResult.Satisfied());
        var mutable = new ArrayList<>(List.of(attempt));

        var result = new AwaitResult.Satisfied<>(mutable, null);
        mutable.clear();

        assertEquals(List.of(attempt), result.attempts());
        assertEquals(300, result.totalAttempts());
        assertNull(result.result());
        assertNull(result.orThrow());
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
                () -> new AttemptRecord<>(0, null, null, null, null, null, null)).getMessage());
    }

    @Test
    void sequenceProgressRequiresAnExistingStageAndValidCaptureCount() {
        var condition = diagnosticCondition("first stage", null);
        var sequence = Conditions.captured(condition, condition);
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(sequence, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(sequence, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(sequence, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new AttemptRecord.Progress(sequence, 1, 3));
        assertThrows(NullPointerException.class, () -> new AttemptRecord.Progress(null, 1, 0));
    }
}
