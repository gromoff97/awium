package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.conditioning.conditions.CollectionConditions;
import io.github.gromoff97.awium.conditioning.conditions.Conditions;

import java.util.Collection;
import java.util.List;
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
        ConditionStage<Integer, Integer> preserving =
                Conditions.matches(value -> value > 0);
        ConditionStage<Collection<?>, Object> selected = CollectionConditions.single;
        ConditionStage<Collection<?>, List<Object>> sequence =
                captured(CollectionConditions.single, CollectionConditions.single);

        assertEquals("counted", ordinary.description());
        assertNull(ordinary.explanation());
        Function<? super Integer, ? extends Evaluation<? extends Integer>> first =
                ordinary.newEvaluator();
        Function<? super Integer, ? extends Evaluation<? extends Integer>> second =
                ordinary.newEvaluator();

        assertEquals(1, first.apply(0).result());
        assertEquals(2, first.apply(0).result());
        assertEquals(1, second.apply(0).result());
        assertNotNull(preserving);
        assertNotNull(selected);
        assertNotNull(sequence);
    }
}
