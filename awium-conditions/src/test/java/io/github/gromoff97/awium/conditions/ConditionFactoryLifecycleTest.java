package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.conditions.Conditions;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.CollectionConditions.singleElement;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.result;
import static io.github.gromoff97.awium.conditions.Conditions.conditionPerWait;
import static io.github.gromoff97.awium.conditions.MapConditions.valueFor;
import static io.github.gromoff97.awium.conditions.OptionalConditions.hasValue;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.lang.Thread.interrupted;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConditionFactoryLifecycleTest {

    @AfterEach
    void clearInterruptFlag() {
        interrupted();
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

        var waiting = await(() -> actual).every(ofNanos(1));

        assertEquals(0, factories[0]);
        assertSame(actual, waiting.until(condition));
        assertEquals(1, factories[0]);

        var result = tryAwait(() -> actual).every(ofNanos(1)).until(condition);
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

            Number optionalValue = await(() -> Optional.of(actual))
                    .every(ofNanos(1)).until(optional);
            Number element = await(() -> List.of(actual))
                    .every(ofNanos(1)).until(collection);
            var mapWait = await(() -> Map.of("key", actual)).every(ofNanos(1));
            Number mapValue = mapWait.until(map);
            AwaitResult<Map<String, Number>, Number> mapResult = tryAwait(() -> Map.of("key", actual)).every(ofNanos(1)).until(map);
            assertSame(actual, optionalValue);
            assertSame(actual, element);
            assertSame(actual, mapValue);
            assertSame(actual, mapResult.orThrow());
            assertEquals(2, attempts(mapResult).size());
        }
        assertEquals(8, factories[0]);
    }
}
