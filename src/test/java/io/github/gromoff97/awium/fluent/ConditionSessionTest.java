package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.engine.ConditionAssessment;
import io.github.gromoff97.awium.fluent.Condition.PreservingStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedStage;
import io.github.gromoff97.awium.fluent.ConditionStage;
import java.util.Collection;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.fluent.Conditions.captured;
import static io.github.gromoff97.awium.fluent.ConditionTestRuntime.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConditionSessionTest {

    @Test
    void everyConditionFamilyUsesTheTypedStageContract() {
        ConditionStage<Integer, Integer> ordinary = ConditionRuntime.conditionFactory(
                "counted", () -> {
                    int[] calls = {0};
                    return ignored -> satisfied(++calls[0]);
                });
        PreservingStage<Integer> preserving =
                Conditions.matches(value -> value > 0);
        SelectedStage<Collection<?>, ?> selected = CollectionConditions.single;
        SelectedSequenceStage<Collection<?>, ?> sequence =
                captured(CollectionConditions.single, CollectionConditions.single);

        assertEquals("counted", ConditionRuntime.description(ordinary));
        assertNull(ConditionRuntime.explanation(ordinary));
        Function<? super Integer, ? extends ConditionAssessment<? extends Integer>> first =
                ConditionRuntime.evaluator(ordinary);
        Function<? super Integer, ? extends ConditionAssessment<? extends Integer>> second =
                ConditionRuntime.evaluator(ordinary);

        assertEquals(1, result(first.apply(0).evaluation()));
        assertEquals(2, result(first.apply(0).evaluation()));
        assertEquals(1, result(second.apply(0).evaluation()));
        assertNotNull(preserving);
        assertNotNull(selected);
        assertNotNull(sequence);
    }
}
