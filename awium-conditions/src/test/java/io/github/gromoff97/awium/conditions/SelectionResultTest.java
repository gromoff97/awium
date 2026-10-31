package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.attempts;
import static io.github.gromoff97.awium.conditions.ConditionAssertions.observation;
import static io.github.gromoff97.awium.conditions.Conditions.instanceOf;
import static io.github.gromoff97.awium.conditions.Conditions.isNull;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SelectionResultTest {

    @Test
    void narrowingKeepsTheOriginalObservationAndReturnsItsConcreteType() {
        var actual = new BigDecimal("42.00");
        int[] reads = {0};
        Callable<Number> source = () -> {
            reads[0]++;
            return actual;
        };

        AwaitResult<Number, BigDecimal> result = tryAwait(source).until(instanceOf(BigDecimal.class));

        BigDecimal selected = result.orThrow();
        assertSame(actual, selected);
        assertSame(selected, result.orThrow());
        assertSame(actual, observation(attempts(result).getFirst()));
        assertEquals(1, reads[0]);
    }

    @Test
    void retainsLegitimateNullAndSelectedOptionalValue() {
        AwaitResult<String, String> nullSource = tryAwait((Callable<String>) () -> null).until(isNull);
        AwaitResult<String, String> nullPredicate = tryAwait((Callable<String>) () -> null).until(matches(actual -> actual == null));
        AwaitResult<Optional<String>, String> selected =
                tryAwait(() -> Optional.of("payment")).until(present);

        assertNull(nullSource.orThrow());
        assertNull(nullPredicate.orThrow());
        var observation = attempts(nullSource).getFirst();
        assertInstanceOf(Attempt.SatisfiedObservation.class, observation);
        assertNull(observation(observation));
        assertEquals("payment", selected.orThrow());
    }
}
