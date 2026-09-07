package io.github.gromoff97.awium;

import static io.github.gromoff97.awium.ConditionFactories.captured;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.ConditionResult.satisfied;

import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static io.github.gromoff97.awium.Await.await;
import static java.time.Duration.ofNanos;

class ConditionSessionTest {

    @Test
    void mapAndOptionalCompositionCreateOneIndependentSessionPerWait() {
        int[] factories = {0};
        var nested = Conditions.conditionFactory("two observations", () -> {
            factories[0]++;
            int[] calls = {0};
            return (String value) -> ++calls[0] == 2 ? satisfied(value) : ConditionResult.unsatisfied("first observation");
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
        Condition<Integer, Integer> ordinary = ConditionFactories.conditionFactory(
                "counted", () -> {
                    int[] calls = {0};
                    return ignored -> satisfied(++calls[0]);
                });
        PreservingCondition<Integer> preserving =
                Conditions.matches(value -> value > 0);
        SelectedCondition<Collection<?>, ?> selected = CollectionConditions.single;
        Condition<Collection<?>, java.util.List<Object>> sequence =
                captured(CollectionConditions.single, CollectionConditions.single);

        assertEquals("counted", ordinary.metadata.description());
        assertNull(ordinary.metadata.explanation());
        Function<? super Integer, ? extends ConditionResult<? extends Integer>> first =
                ordinary.newSession();
        Function<? super Integer, ? extends ConditionResult<? extends Integer>> second =
                ordinary.newSession();

        assertEquals(1, result(first.apply(0)));
        assertEquals(2, result(first.apply(0)));
        assertEquals(1, result(second.apply(0)));
        assertNotNull(preserving);
        assertNotNull(selected);
        assertNotNull(sequence);
    }
}
