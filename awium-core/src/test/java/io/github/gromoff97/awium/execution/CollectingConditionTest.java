package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.CollectingCondition;
import io.github.gromoff97.awium.AwaitCondition.InputCondition;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CollectingConditionTest {
    @Test
    void aStandaloneCollectorExposesItselfAsItsOnlyStage() {
        var condition = new RecordOnce();
        assertEquals(List.of(condition), condition.stages());
    }

    @Test
    void explainedCopiesKeepTheDeclaredStages() {
        var original = new RecordOnce();
        assertEquals(original.stages(), original.because("record the observation").stages());
    }

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
        var explained = original.because("old reason").because("record the observation");
        var result = tryAwait(() -> "ready").until(explained);

        assertNull(original.explanation());
        assertSame(explained, result.conditions().getFirst().condition());
        assertEquals("record the observation", result.conditions().getFirst().condition().explanation());
    }

    @Test
    void newRunsKeepTheirCollectedValuesIndependent() throws Throwable {
        var condition = new RecordOnce();
        var first = ConditionExecution.<String, List<String>>start(condition);
        var second = ConditionExecution.<String, List<String>>start(condition);
        first.probe("first");

        assertFalse(second.complete());
        second.probe("second");
        assertEquals(List.of("first"), first.result());
        assertEquals(List.of("second"), second.result());
    }

    private static final class RecordOnce implements CollectingCondition<InputCondition<Object>> {
        @Override public String description() { return "record one observation"; }

        @Override
        public ConditionRun<?, ?> newRun(AwaitCondition definition) {
            return new ConditionRun<Object, List<Object>>(definition) {
                private List<Object> values = List.of();

                @Override
                public AttemptResult<Object> probe(Object actual) {
                    values = singletonList(actual);
                    return satisfied(actual);
                }

                @Override public boolean complete() { return !values.isEmpty(); }
                @Override public List<Object> result() { return values; }
            };
        }
    }
}
