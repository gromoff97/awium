package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.Condition.MapValueCondition;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.ConditionSupport.bind;
import static io.github.gromoff97.awium.ConditionTestRuntime.evaluated;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.internal.AttemptResult.Uncontrolled;
import static io.github.gromoff97.awium.internal.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AttemptFactoryTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void attemptsKeepTheirBindingAndEachCallReturnsAnIndependentTypedResult() {
        int[] factories = {0};
        var observations = new ArrayList<String>();
        Typed<String, Integer> condition = new Typed<>("count calls", null, () -> {
            factories[0]++;
            return actual -> {
                observations.add(actual);
                return satisfied(observations.size());
            };
        });
        var factory = bind(condition);
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
        Typed<String, Integer> condition = new Typed<>("three calls", null,
                () -> actual -> switch (++calls[0]) {
                    case 1 -> throw failure;
                    case 2 -> null;
                    default -> satisfied(actual.length());
                });
        var attempt = bind(condition).create("ready");

        var first = attempt.call();
        var second = attempt.call();
        var third = attempt.call();

        assertSame(failure, assertInstanceOf(Uncontrolled.class, first).cause());
        var invalid = assertInstanceOf(Uncontrolled.class, second).cause();
        assertInstanceOf(NullPointerException.class, invalid);
        assertEquals("condition returned null evaluation", invalid.getMessage());
        assertEquals(5, result(third));
    }

    @Test
    void nullIsAValidBoundObservation() {
        Typed<String, String> condition = new Typed<>("preserve null", null,
                () -> AttemptResult::satisfied);
        var attempt = bind(condition).create(null);

        assertNull(attempt.observed());
        assertNull(result(attempt.call()));
    }

    @Test
    void interruptedCallDoesNotInitializeTheConditionAndCanBeRetriedAfterClearingTheFlag() {
        int[] factories = {0};
        Typed<String, String> condition = new Typed<>("preserve", null, () -> {
            factories[0]++;
            return AttemptResult::satisfied;
        });
        var attempt = bind(condition).create("ready");

        currentThread().interrupt();
        var interruption = assertInstanceOf(Uncontrolled.class, attempt.call());
        assertInstanceOf(InterruptedException.class, interruption.cause());
        assertEquals(0, factories[0]);
        interrupted();

        assertEquals("ready", result(attempt.call()));
        assertEquals(1, factories[0]);
    }

    @Test
    void preservingFactoryIsLazyIndependentAndReturnsTheOriginalObservation() {
        int[] factories = {0};
        var actual = new Object();
        var condition = conditionPerWait("two observations", () -> {
            factories[0]++;
            int[] calls = {0};
            return ignored -> ++calls[0] == 2 ? satisfied() : unsatisfied("first observation");
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
            return ignored -> ++calls[0] == 2 ? satisfied() : unsatisfied("first observation");
        });
        var optional = hasValue(nested);
        var collection = singleElement(nested);
        MapValueCondition<Map<? super String, ? extends Number>> map =
                valueFor("key", nested);
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
        Typed<Integer, Integer> ordinary = new Typed<>(
                "counted", null, () -> {
                    int[] calls = {0};
                    return ignored -> satisfied(++calls[0]);
                });
        assertEquals("counted", ordinary.description());
        var first = bind(ordinary).create(0);
        var second = bind(ordinary).create(0);

        assertEquals(1, result(evaluated(first.call())));
        assertEquals(2, result(evaluated(first.call())));
        assertEquals(1, result(evaluated(second.call())));
    }
}
