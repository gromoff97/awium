package io.github.gromoff97.awium.condition;

import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.captured;

import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.FakeTime;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;
import io.github.gromoff97.awium.conditions.CollectionConditions;
import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.conditions.MapConditions;
import io.github.gromoff97.awium.conditions.OptionalConditions;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;

import static io.github.gromoff97.awium.condition.ConditionTestRuntime.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static io.github.gromoff97.awium.await.Await.await;
import static java.time.Duration.ofNanos;

class ConditionSessionTest {

    @Test
    void mapAndOptionalCompositionCreateOneIndependentSessionPerWait() {
        int[] factories = {0};
        var nested = Conditions.conditionFactory("two observations", () -> {
            factories[0]++;
            int[] calls = {0};
            return (String value) -> ++calls[0] == 2 ? satisfied(value) : ConditionEvaluation.unsatisfied("first observation");
        });
        Condition<Map<String, String>, String> map = MapConditions.valueFor("key", nested);
        var optional = OptionalConditions.hasValue(nested);
        assertEquals(0, factories[0]);
        for (int run = 0; run < 2; run++) {
            var time = new FakeTime(0);
            assertEquals("ready", await(() -> Map.of("key", "ready")).usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(3)).until(map));
            assertEquals("ready", await(() -> Optional.of("ready")).usingTime(time, time)
                    .every(ofNanos(1)).upTo(ofNanos(3)).until(optional));
        }
        assertEquals(4, factories[0]);
    }

    @Test
    void conditionFamiliesKeepMetadataAndIndependentEvaluators() {
        Condition<Integer, Integer> ordinary = ConditionRuntime.conditionFactory(
                "counted", () -> {
                    int[] calls = {0};
                    return ignored -> satisfied(++calls[0]);
                });
        PreservingCondition<Integer> preserving =
                Conditions.matches(value -> value > 0);
        SelectedCondition<Collection<?>, ?> selected = CollectionConditions.single;
        Condition<Collection<?>, java.util.List<Object>> sequence =
                captured(CollectionConditions.single, CollectionConditions.single);

        assertEquals("counted", ConditionRuntime.metadata(ordinary).description());
        assertNull(ConditionRuntime.metadata(ordinary).explanation());
        Function<? super Integer, ? extends ConditionEvaluation<? extends Integer>> first =
                ConditionRuntime.evaluator(ordinary);
        Function<? super Integer, ? extends ConditionEvaluation<? extends Integer>> second =
                ConditionRuntime.evaluator(ordinary);

        assertEquals(1, result(first.apply(0)));
        assertEquals(2, result(first.apply(0)));
        assertEquals(1, result(second.apply(0)));
        assertNotNull(preserving);
        assertNotNull(selected);
        assertNotNull(sequence);
    }
}
