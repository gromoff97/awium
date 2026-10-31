package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.ExecutionStep;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase.ACQUISITION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static java.util.Arrays.asList;

class AttemptHistoryTest {

    @Test
    void snapshotsRemainUnchangedAsTheLastNumberMovesAndAnotherStateBegins() {
        var history = new AttemptHistory<String>(true);
        var condition = diagnosticCondition("ready", null);
        var definitions = List.<Condition>of(condition);
        var empty = history.conditions(definitions).getFirst().attempts();
        var first = entry(condition, new String("pending"), unsatisfied("pending"));
        var second = entry(condition, new String("pending"), unsatisfied("pending"));
        var third = entry(condition, "third", satisfied("third"));
        history.add(first);
        var before = history.conditions(definitions).getFirst().attempts();
        history.add(second);
        var repeated = history.conditions(definitions).getFirst().attempts();
        assertThrows(NullPointerException.class, () -> history.add(null));
        assertEquals(2, history.count());
        history.add(third);

        assertTrue(empty.isEmpty());
        assertEquals(Map.of(1L, first.attempt()), before);
        assertEquals(Map.of(2L, second.attempt()), repeated);
        assertSame(second.attempt(), repeated.lastEntry().getValue());
        assertEquals(Map.of(2L, second.attempt(), 3L, third.attempt()),
                history.conditions(definitions).getFirst().attempts());
        assertEquals(3, history.count());
        assertThrows(UnsupportedOperationException.class, repeated::pollLastEntry);
    }

    @Test
    void countsWithoutRetainingOrInspectingRecordsForOrdinaryAwait() {
        var history = new AttemptHistory<String>(false);
        var cause = new AssertionError("unreadable") {
            @Override public String getMessage() { throw new AssertionError("must not be read"); }
        };
        var condition = diagnosticCondition("ready", null);
        var attempt = entry(condition, "pending", unsatisfied("waiting", cause));
        assertEquals(0, history.count());
        for (int i = 0; i < 300; i++) history.add(attempt);
        assertEquals(300, history.count());
        assertTrue(history.conditions(List.of(condition)).getFirst().attempts().isEmpty());
    }

    @Test
    void diagnosticChangesRemainInOrderEvenWhenAnEarlierMessageReturns() {
        var history = new AttemptHistory<String>(true);
        var condition = diagnosticCondition("ready", null);
        for (String mismatch : List.of("queued", "queued", "running", "queued", "queued")) {
            history.add(entry(condition, "observation", unsatisfied(mismatch)));
        }
        var attempts = history.conditions(List.of(condition)).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 3L, 5L);
        assertThat(attempts.values())
                .extracting(attempt -> assertInstanceOf(Attempt.Unsatisfied.class, attempt).mismatch())
                .containsExactly("queued", "running", "queued");
    }

    @Test
    void observationEqualityHandlesNullArraysAndReturningToAnEarlierValue() {
        var history = new AttemptHistory<Object>(true);
        var condition = diagnosticCondition("ready", null);
        var observations = asList(null, null,
                new int[]{1}, new int[]{1}, new int[]{2}, new int[]{2}, new int[]{1});
        for (Object observation : observations) history.add(entry(condition, observation, unsatisfied()));
        var attempts = history.conditions(List.of(condition)).getFirst().attempts();
        assertThat(attempts.keySet()).containsExactly(2L, 4L, 6L, 7L);
        attempts.forEach((through, attempt) -> {
            assertInstanceOf(Attempt.Unsatisfied.class, attempt);
            assertSame(observations.get(through.intValue() - 1), observation(attempt));
        });
    }

    private static <OBSERVED> ExecutionStep<OBSERVED> entry(Condition condition, OBSERVED observed, ConditionResult<?> status) {
        return new ExecutionStep<>(condition, 1, 0, ACQUISITION, 0, switch (status) {
            case ConditionResult.Satisfied<?> ignored -> new Attempt.Satisfied<>(observed);
            case ConditionResult.Unsatisfied<?> mismatch -> new Attempt.Unsatisfied<>(observed, mismatch.mismatch(), mismatch.cause());
        });
    }
}
