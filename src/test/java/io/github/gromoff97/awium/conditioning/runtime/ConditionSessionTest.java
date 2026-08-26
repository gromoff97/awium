package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConditionSessionTest {

    @Test
    void oneEvaluatorRetainsProgressAndTheNextEvaluatorStartsFresh() {
        Condition<Integer, Integer> condition = ConditionRuntime.condition(
                "counted", () -> {
                    int[] calls = {0};
                    return ignored -> satisfied(++calls[0]);
                });

        Function<Integer, Evaluation<Integer>> first =
                ConditionRuntime.evaluator(condition);
        Function<Integer, Evaluation<Integer>> second =
                ConditionRuntime.evaluator(condition);

        assertEquals(1, first.apply(0).result());
        assertEquals(2, first.apply(0).result());
        assertEquals(1, second.apply(0).result());
    }
}
