package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.result.HistoryEntry.Phase.ACQUISITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttemptHistoryTest {

    @Test
    void snapshotsStayUnchangedWhenOlderAttemptsAreDiscarded() {
        var history = new AttemptHistory<String>(2);
        var empty = history.snapshot();
        var condition = diagnosticCondition("ready", null);
        var timing = new HistoryEntry.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        var first = new HistoryEntry<>(ACQUISITION, timing, condition, null, "first", new AttemptStatus.Satisfied());
        var second = new HistoryEntry<>(ACQUISITION, timing, condition, null, "second", new AttemptStatus.Satisfied());
        var third = new HistoryEntry<>(ACQUISITION, timing, condition, null, "third", new AttemptStatus.Satisfied());
        history.add(first);
        history.add(second);
        var snapshot = history.snapshot();

        assertThrows(NullPointerException.class, () -> history.add(null));
        assertEquals(List.of(first, second), history.snapshot());
        assertEquals(2, history.count());
        history.add(third);

        assertEquals(List.of(), empty);
        assertEquals(List.of(first, second), snapshot);
        assertEquals(List.of(second, third), history.snapshot());
        assertEquals(3, history.count());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void countsAttemptsWithoutRetainingRecords() {
        var history = new AttemptHistory<String>(0);
        var timing = new HistoryEntry.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);
        var attempt = new HistoryEntry<>(ACQUISITION, timing, diagnosticCondition("ready", null),
                null, "ready", new AttemptStatus.Satisfied());

        assertEquals(0, history.count());
        for (int i = 0; i < 300; i++) {
            history.add(attempt);
        }

        assertEquals(300, history.count());
        assertEquals(List.of(), history.snapshot());
    }
}
