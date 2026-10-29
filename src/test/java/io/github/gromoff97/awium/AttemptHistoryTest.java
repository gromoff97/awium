package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.HistoryEntry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.result.HistoryEntry.Phase.ACQUISITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttemptHistoryTest {

    @Test
    void snapshotsRemainUnchangedAsAGroupGrowsAndAnotherBegins() {
        var history = new AttemptHistory<String>(true);
        var empty = history.snapshot();
        var condition = diagnosticCondition("ready", null);
        var first = entry(condition, "first", unsatisfied("pending"));
        var second = entry(condition, "second", unsatisfied("pending"));
        var third = entry(condition, "third", satisfied());
        history.add(first);
        var before = history.snapshot();
        history.add(second);
        var repeated = history.snapshot();

        assertThrows(NullPointerException.class, () -> history.add(null));
        assertEquals(2, history.count());
        history.add(third);

        assertEquals(List.of(), empty);
        assertEquals(1, before.getFirst().count());
        assertSame(first, before.getFirst().last());
        assertEquals(2, repeated.getFirst().count());
        assertSame(first, repeated.getFirst().first());
        assertSame(second, repeated.getFirst().last());
        assertEquals(2, history.snapshot().size());
        assertEquals(3, history.snapshot().getLast().firstNumber());
        assertSame(third, history.snapshot().getLast().first());
        assertEquals(3, history.count());
        assertThrows(UnsupportedOperationException.class, repeated::clear);
    }

    @Test
    void countsWithoutRetainingOrInspectingRecordsForOrdinaryAwait() {
        var history = new AttemptHistory<String>(false);
        var cause = new AssertionError("unreadable") {
            @Override public String getMessage() { throw new AssertionError("must not be read"); }
        };
        var attempt = entry(diagnosticCondition("ready", null), "pending", unsatisfied("waiting", cause));

        assertEquals(0, history.count());
        for (int i = 0; i < 300; i++) history.add(attempt);

        assertEquals(300, history.count());
        assertEquals(List.of(), history.snapshot());
    }

    @Test
    void repeatedDefinitionsGroupWhileIndependentCopiesRemainSeparate() {
        var history = new AttemptHistory<String>(true);
        var condition = diagnosticCondition("ready", null).because("reason");
        history.add(entry(condition, "one", unsatisfied()));
        history.add(entry(condition, "two", unsatisfied()));
        history.add(entry(condition.because("reason"), "three", unsatisfied()));
        history.add(entry(diagnosticCondition("ready", null).because("reason"), "four", unsatisfied()));

        assertEquals(List.of(2L, 1L, 1L), history.snapshot().stream().map(group -> group.count()).toList());
    }

    @Test
    void diagnosticChangesRemainInOrderEvenWhenAnEarlierMessageReturns() {
        var history = new AttemptHistory<String>(true);
        var condition = diagnosticCondition("ready", null);
        for (String mismatch : List.of("queued", "queued", "running", "queued", "queued")) {
            history.add(entry(condition, "observation", unsatisfied(mismatch)));
        }

        assertEquals(List.of(2L, 1L, 2L), history.snapshot().stream().map(group -> group.count()).toList());
        assertEquals(List.of(1L, 3L, 4L), history.snapshot().stream().map(group -> group.firstNumber()).toList());
        assertEquals(List.of(2L, 3L, 5L), history.snapshot().stream().map(group -> group.lastNumber()).toList());
    }

    private static HistoryEntry<String> entry(Condition condition, String observed, AttemptStatus status) {
        return new HistoryEntry<>(ACQUISITION,
                new HistoryEntry.Timing(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO),
                condition, null, observed, status);
    }
}
