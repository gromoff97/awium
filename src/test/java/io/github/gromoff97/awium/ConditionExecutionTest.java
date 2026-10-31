package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.ConditionResult;
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
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.ConditionSupport.bind;
import static io.github.gromoff97.awium.ConditionTestRuntime.result;
import static io.github.gromoff97.awium.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.MapConditions.valueFor;
import static io.github.gromoff97.awium.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ConditionExecutionTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
    }

    @Test
    void eachProbeReturnsAnIndependentResultAndSharesOneLazyEvaluator() throws Throwable {
        int[] factories = {0};
        var observations = new ArrayList<String>();
        Typed<String, Integer> condition = new Typed<>("count calls", null, () -> {
            factories[0]++;
            return actual -> {
                observations.add(actual);
                return ConditionResult.satisfied(observations.size());
            };
        });
        var execution = bind(condition);

        assertEquals(0, factories[0]);
        var firstResult = execution.probe("first");
        var secondResult = execution.probe("second");
        var repeatedResult = execution.probe("first");

        assertEquals(1, result(firstResult));
        assertEquals(2, result(secondResult));
        assertEquals(3, result(repeatedResult));
        assertEquals(List.of("first", "second", "first"), observations);
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
                    default -> ConditionResult.satisfied(actual.length());
                });
        var execution = bind(condition);

        assertSame(failure, assertThrows(Throwable.class, () -> execution.probe("ready")));
        var invalid = assertThrows(NullPointerException.class, () -> execution.probe("ready"));
        assertEquals("condition returned null ConditionResult", invalid.getMessage());
        assertEquals(5, result(assertDoesNotThrow(() -> execution.probe("ready"))));
    }

    @Test
    void nullIsAValidObservation() throws Throwable {
        Typed<String, String> condition = new Typed<>("preserve null", null,
                () -> ConditionResult::satisfied);
        assertNull(result(bind(condition).probe(null)));
    }

    @Test
    void interruptedCallDoesNotInitializeTheConditionAndCanBeRetriedAfterClearingTheFlag() throws Throwable {
        int[] factories = {0};
        Typed<String, String> condition = new Typed<>("preserve", null, () -> {
            factories[0]++;
            return ConditionResult::satisfied;
        });
        var execution = bind(condition);

        currentThread().interrupt();
        assertThrows(InterruptedException.class, () -> execution.probe("ready"));
        assertEquals(0, factories[0]);
        interrupted();

        assertEquals("ready", result(execution.probe("ready")));
        assertEquals(1, factories[0]);
    }

    @Test
    void customFactoryIsLazyIndependentAndCanReturnTheOriginalObservation() {
        int[] factories = {0};
        var actual = new Object();
        var condition = conditionPerWait("two observations", () -> {
            factories[0]++;
            int[] calls = {0};
            return ignored -> ++calls[0] == 2 ? satisfied(ignored) : unsatisfied("first observation");
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
        var nested = Conditions.conditionPerWait("second observation", () -> {
            factories[0]++;
            int[] calls = {0};
            return (Number ignored) -> ++calls[0] == 2 ? satisfied(ignored) : unsatisfied("first observation");
        });
        var optional = hasValue(nested);
        var collection = singleElement(nested);
        var map = valueFor("key", nested);
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
                    return ignored -> ConditionResult.satisfied(++calls[0]);
                });
        assertEquals("counted", ordinary.description());
        var first = bind(ordinary);
        var second = bind(ordinary);

        assertEquals(1, result(assertDoesNotThrow(() -> first.probe(0))));
        assertEquals(2, result(assertDoesNotThrow(() -> first.probe(0))));
        assertEquals(1, result(assertDoesNotThrow(() -> second.probe(0))));
    }
}
