package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.AwaitCondition.InputCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CollectingConditionTest {
    @Test
    void untilInfersCollectedObservationTypesFromTheSource() {
        var actual = "ready";
        var values = await(() -> actual).until(new RecordOnce());
        assertEquals(5, values.getFirst().length());
        assertSame(actual, values.getFirst());
    }

    @Test
    void tryAwaitInfersCollectedObservationTypesFromTheSource() {
        var actual = "ready";
        var outcome = tryAwait(() -> actual).until(new RecordOnce());
        assertEquals(5, outcome.orThrow().getFirst().length());
        assertSame(actual, outcome.orThrow().getFirst());
    }

    @Test
    void explainedCopiesPassTheirDefinitionToTheRun() {
        var original = new RecordOnce();
        var explained = original.because("record the observation");
        var result = tryAwait(() -> "ready").until(explained);

        assertNull(original.explanation());
        assertSame(explained, result.conditions().getFirst().condition());
        assertEquals("record the observation", result.conditions().getFirst().condition().explanation());
    }

    @Test
    void newRunsKeepTheirCollectedValuesIndependent() throws Throwable {
        var condition = new RecordOnce();
        var first = ConditionExecution.<String, List<String>>start(condition, false);
        var second = ConditionExecution.<String, List<String>>start(condition, false);
        first.probe("first");

        assertFalse(second.complete());
        second.probe("second");
        assertEquals(List.of("first"), first.result());
        assertEquals(List.of("second"), second.result());
    }

    private static final class RecordOnce extends CollectingCondition<InputCondition<Object>> {
        private RecordOnce() { super("record one observation", List.of(), null); }
        private RecordOnce(RecordOnce original, String reason) { super(original, reason); }

        @Override
        public RecordOnce because(String reason) { return new RecordOnce(this, reason); }

        @Override
        protected <OBSERVED> ConditionRun<OBSERVED, List<OBSERVED>> collectingRun(AwaitCondition definition, boolean recording) {
            return new AbstractConditionRun<>(definition, recording) {
                private List<OBSERVED> values = List.of();

                @Override
                public ConditionResult<OBSERVED> probe(OBSERVED actual) {
                    values = singletonList(actual);
                    return satisfied(actual);
                }

                @Override public boolean complete() { return !values.isEmpty(); }
                @Override public List<OBSERVED> result() { return values; }
            };
        }
    }
}
