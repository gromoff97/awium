package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttemptFactoryTest {

    @Test
    void attemptsKeepTheirBindingAndEachCallReturnsAnIndependentTypedResult() {
        int[] factories = {0};
        var observations = new ArrayList<String>();
        Condition.Typed<String, Integer> condition = new Condition.Typed<>("count calls", null, () -> {
            factories[0]++;
            return actual -> {
                observations.add(actual);
                return AttemptResult.satisfied(observations.size());
            };
        });
        var factory = ConditionSupport.bind(condition);
        var first = factory.create("first");
        var second = factory.create("second");

        assertEquals(0, factories[0]);
        assertEquals("first", first.observed());
        var firstResult = first.call();
        var secondResult = second.call();
        var repeatedResult = first.call();

        assertEquals(1, result(firstResult));
        assertEquals(2, result(secondResult));
        assertEquals(3, result(repeatedResult));
        assertEquals(List.of("first", "second", "first"), observations);
        assertEquals("first", first.observed());
        assertEquals(1, factories[0]);
    }

    @Test
    void thrownAndInvalidResultsBelongToTheirOwnCalls() {
        var failure = new AssertionError("first call failed");
        int[] calls = {0};
        Condition.Typed<String, Integer> condition = new Condition.Typed<>("three calls", null,
                () -> actual -> switch (++calls[0]) {
                    case 1 -> throw failure;
                    case 2 -> null;
                    default -> AttemptResult.satisfied(actual.length());
                });
        var attempt = ConditionSupport.bind(condition).create("ready");

        var first = attempt.call();
        var second = attempt.call();
        var third = attempt.call();

        assertSame(failure, assertInstanceOf(AttemptResult.Uncontrolled.class, first).cause());
        var invalid = assertInstanceOf(AttemptResult.Uncontrolled.class, second).cause();
        assertInstanceOf(NullPointerException.class, invalid);
        assertEquals("condition returned null evaluation", invalid.getMessage());
        assertEquals(5, result(third));
    }

    @Test
    void nullIsAValidBoundObservation() {
        Condition.Typed<String, String> condition = new Condition.Typed<>("preserve null", null,
                () -> AttemptResult::satisfied);
        var attempt = ConditionSupport.bind(condition).create(null);

        assertNull(attempt.observed());
        assertNull(result(attempt.call()));
    }

    @Test
    void interruptedCallDoesNotInitializeTheConditionAndCanBeRetriedAfterClearingTheFlag() {
        int[] factories = {0};
        Condition.Typed<String, String> condition = new Condition.Typed<>("preserve", null, () -> {
            factories[0]++;
            return AttemptResult::satisfied;
        });
        var attempt = ConditionSupport.bind(condition).create("ready");

        Thread.currentThread().interrupt();
        try {
            var interrupted = assertInstanceOf(AttemptResult.Uncontrolled.class, attempt.call());
            assertInstanceOf(InterruptedException.class, interrupted.cause());
            assertEquals(0, factories[0]);
        } finally {
            Thread.interrupted();
        }

        assertEquals("ready", result(attempt.call()));
        assertEquals(1, factories[0]);
    }

    @Test
    void preservingFactoryIsLazyIndependentAndReturnsTheOriginalObservation() {
        int[] factories = {0};
        var actual = new Object();
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

        var result = diagnostic(waiting).until(condition);
        assertSame(actual, result.orThrow());
        assertEquals(2, attempts(result).size());
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
            AwaitResult<Map<String, Number>, Number> mapResult = diagnostic(mapWait).until(map);
            assertSame(actual, optionalValue);
            assertSame(actual, element);
            assertSame(actual, mapValue);
            assertSame(actual, mapResult.orThrow());
            assertEquals(2, attempts(mapResult).size());
        }
        assertEquals(8, factories[0]);
    }

    @Test
    void ordinaryConditionKeepsMetadataAndCreatesIndependentEvaluators() {
        Condition.Typed<Integer, Integer> ordinary = new Condition.Typed<>(
                "counted", null, () -> {
                    int[] calls = {0};
                    return ignored -> AttemptResult.satisfied(++calls[0]);
                });
        assertEquals("counted", ordinary.description());
        var first = ConditionSupport.bind(ordinary).create(0);
        var second = ConditionSupport.bind(ordinary).create(0);

        assertEquals(1, result(ConditionTestRuntime.evaluated(first.call())));
        assertEquals(2, result(ConditionTestRuntime.evaluated(first.call())));
        assertEquals(1, result(ConditionTestRuntime.evaluated(second.call())));
    }
}
