package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
import io.github.gromoff97.awium.conditioning.conditions.Conditions;

import java.util.Collection;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.conditions.Conditions.captured;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConditionSessionTest {

    @Test
    void everyConditionFamilyUsesTheTypedStageContract() {
        ConditionStage<Integer, Integer> ordinary = ConditionRuntime.condition(
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
        Function<? super Integer, ? extends Evaluation<? extends Integer>> first =
                ConditionRuntime.evaluator(ordinary);
        Function<? super Integer, ? extends Evaluation<? extends Integer>> second =
                ConditionRuntime.evaluator(ordinary);

        assertEquals(1, first.apply(0).result());
        assertEquals(2, first.apply(0).result());
        assertEquals(1, second.apply(0).result());
        assertNotNull(preserving);
        assertNotNull(selected);
        assertNotNull(sequence);
    }
}
