package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.result.HistoryEntry.Phase.ACQUISITION;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AwaitResultTest {

    @Test
    void resultsCopyAttemptsAndSuccessRetainsLegitimateNull() {
        var attempt = new HistoryEntry<String>(ACQUISITION,
                new HistoryEntry.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                diagnosticCondition("ready", null), null,
                null, new AttemptStatus.Satisfied());
        var mutable = new ArrayList<>(List.of(attempt));

        var result = new AwaitResult.Satisfied<>(mutable, null);
        var failed = new AwaitResult.Failed<>(mutable,
                new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of()));
        mutable.clear();

        for (var outcome : List.of(result, failed)) {
            assertEquals(List.of(attempt), outcome.attempts());
            assertSame(attempt, outcome.attempts().getFirst());
            assertThrows(UnsupportedOperationException.class, () -> outcome.attempts().clear());
        }
        assertNull(result.result());
        assertNull(result.orThrow());
    }

    @Test
    void failedResultRequiresStructuredFailure() {
        var failure = new AwaitFailure(AwaitFailure.Reason.SOURCE_FAILED, "failed",
                new IllegalStateException("failed"), List.of());
        var result = new AwaitResult.Failed<String, String>(List.of(), failure);

        assertSame(failure, result.failure());
        assertEquals(0, result.attempts().size());
        assertThrows(NullPointerException.class,
                () -> new AwaitResult.Failed<String, String>(result.attempts(), null));
    }

    @Test
    void resultsRequireAttemptsAndRejectNullEntries() {
        var failure = new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "timeout", null, List.of());
        assertThrows(NullPointerException.class, () -> new AwaitResult.Satisfied<>(null, "ready"));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<>(null, failure));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Satisfied<>(singletonList(null), "ready"));
        assertThrows(NullPointerException.class, () -> new AwaitResult.Failed<>(singletonList(null), failure));
    }

    @Test
    void sequenceProgressRequiresAnExistingStageAndValidCaptureCount() {
        var condition = diagnosticCondition("first stage", null);
        var sequence = Conditions.captured(condition, condition);
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry.Progress(sequence, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry.Progress(sequence, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry.Progress(sequence, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new HistoryEntry.Progress(sequence, 1, 3));
        assertThrows(NullPointerException.class, () -> new HistoryEntry.Progress(null, 1, 0));
    }
}
