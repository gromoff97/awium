package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.internal.AwaitAttempt;
import io.github.gromoff97.awium.internal.ConditionEvaluation;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionAwaitAttemptTest {

    @Test
    void preservingFactoryIsLazyIndependentAndReturnsTheOriginalObservation() {
        int[] factories = {0};
        var actual = new Object();
        var replacement = new Object();
        var condition = Conditions.conditionPerWait("two observations", () -> {
            factories[0]++;
            int[] calls = {0};
            return ignored -> ++calls[0] == 2 ? satisfied() : ConditionResult.unsatisfied("first observation");
        }).because("second observation is required");
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        assertEquals(0, factories[0]);
        assertSame(actual, waiting.until(condition));
        assertEquals(1, factories[0]);

        var result = waiting.tryUntil(condition);
        assertSame(actual, result.orThrow());
        assertEquals(2, result.totalAttempts());
        assertEquals(2, factories[0]);
    }

    @Test
    void nestedChecksPreserveSelectedIdentityAndIndependentSessions() {
        int[] factories = {0};
        Number actual = new java.math.BigDecimal("1.00");
        var nested = Conditions.<Number>conditionPerWait("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return ignored -> ++calls[0] == 2 ? satisfied() : ConditionResult.unsatisfied("first observation");
        });
        var optional = OptionalConditions.hasValue(nested);
        var collection = CollectionConditions.singleElement(nested);
        Condition.MapValueCondition<Map<? super String, ? extends Number>> map =
                MapConditions.valueFor("key", nested);
        assertEquals(0, factories[0]);

        for (int run = 0; run < 2; run++) {
            var time = new FakeTime(0);
            Number optionalValue = await(() -> Optional.of(actual)).usingTime(time, time)
                    .every(ofNanos(1)).until(optional);
            Number element = await(() -> List.of(actual)).usingTime(time, time)
                    .every(ofNanos(1)).until(collection);
            var mapWait = await(() -> Map.of("key", actual)).usingTime(time, time).every(ofNanos(1));
            Number mapValue = mapWait.until(map);
            AwaitResult<Map<String, Number>, Number> mapResult = mapWait.tryUntil(map);
            assertSame(actual, optionalValue);
            assertSame(actual, element);
            assertSame(actual, mapValue);
            assertSame(actual, mapResult.orThrow());
            assertEquals(2, mapResult.totalAttempts());
        }
        assertEquals(8, factories[0]);
    }

    @Test
    void ordinaryConditionKeepsMetadataAndCreatesIndependentEvaluators() throws Throwable {
        Condition.Typed<Integer, Integer> ordinary = new Condition.Typed<>(
                "counted", null, () -> {
                    int[] calls = {0};
                    return ignored -> ConditionEvaluation.satisfied(++calls[0]);
                });
        assertEquals("counted", ordinary.description());
        AwaitAttempt<Integer, Integer> first = ConditionSupport.bind(ordinary);
        AwaitAttempt<Integer, Integer> second = ConditionSupport.bind(ordinary);

        assertEquals(1, result(ConditionTestRuntime.evaluated(first.probe(0))));
        assertEquals(2, result(ConditionTestRuntime.evaluated(first.probe(0))));
        assertEquals(1, result(ConditionTestRuntime.evaluated(second.probe(0))));
    }
}
