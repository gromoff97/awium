package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            return ignored -> ++calls[0] == 2 ? satisfied(replacement) : ConditionResult.unsatisfied("first observation");
        }).because("the original observation is needed");
        var time = new FakeTime(0);
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(3));

        assertEquals(0, factories[0]);
        assertSame(actual, waiting.until(condition));
        assertEquals(1, factories[0]);

        var result = waiting.tryUntil(condition);
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, result).result());
        assertEquals(2, result.totalAttempts());
        assertEquals(2, factories[0]);
    }

    @Test
    void preservingConditionKeepsTheObservationAcrossTerminalsAndComposition() {
        var actual = new Object();
        var replacement = new Object();
        PreservingCondition<Object> condition = Conditions.condition("preserve", ignored -> satisfied(replacement));
        condition = condition.because("original identity");
        var waiting = await(() -> actual);

        assertSame(actual, waiting.until(condition));
        assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, waiting.tryUntil(condition)).result());
        assertSame(actual, await(() -> Optional.of(actual)).until(OptionalConditions.hasValue(condition)));
        assertSame(actual, await(() -> List.of(actual)).until(CollectionConditions.single(condition)));
        var time = new FakeTime(0);
        assertEquals(List.of(actual, actual), waiting.usingTime(time, time).until(condition, condition));
    }

    @Test
    void explainedNestedChecksPreserveSelectedIdentityAndIndependentSessions() {
        int[] factories = {0};
        Number actual = new java.math.BigDecimal("1.00");
        var nested = Conditions.<Number>conditionPerWait("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return ignored -> ++calls[0] == 2 ? satisfied(42) : ConditionResult.unsatisfied("first observation");
        });
        var optional = OptionalConditions.hasValue(nested).because("optional reason");
        var collection = CollectionConditions.single(nested).because("collection reason");
        Condition.MapValueCondition<Map<? super String, ?>, Number, Condition.Reference<?>> map =
                MapConditions.valueFor("key", nested).because("map reason");
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
            assertSame(actual, assertInstanceOf(AwaitResult.Satisfied.class, mapResult).result());
            assertEquals(2, mapResult.totalAttempts());
        }
        assertEquals(8, factories[0]);
    }

    @Test
    void ordinaryConditionKeepsMetadataAndCreatesIndependentEvaluators() throws Throwable {
        Condition<Integer, Integer> ordinary = new Condition<>(
                "counted", null, null, () -> {
                    int[] calls = {0};
                    return ignored -> AttemptResult.of(satisfied(++calls[0]));
                });
        assertEquals("counted", ordinary.description());
        assertNull(ordinary.explanation());
        AwaitAttempt<Integer, Integer> first = new ConditionAwaitAttempt<>(ordinary);
        AwaitAttempt<Integer, Integer> second = new ConditionAwaitAttempt<>(ordinary);

        assertEquals(1, result(ConditionTestRuntime.evaluated(first.probe(0))));
        assertEquals(2, result(ConditionTestRuntime.evaluated(first.probe(0))));
        assertEquals(1, result(ConditionTestRuntime.evaluated(second.probe(0))));
    }
}
