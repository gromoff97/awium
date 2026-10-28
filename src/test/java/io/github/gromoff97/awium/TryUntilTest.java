package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.evaluated;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.isNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TryUntilTest {

    @Test
    void narrowingKeepsTheOriginalObservationAndReturnsItsConcreteType() {
        var actual = new BigDecimal("42.00");
        int[] reads = {0};
        Callable<Number> source = () -> {
            reads[0]++;
            return actual;
        };
        var time = new FakeTime(0);

        AwaitResult<Number, BigDecimal> result = await(source).usingTime(time, time).tryUntil(Conditions.instanceOf(BigDecimal.class));

        BigDecimal selected = result.orThrow();
        assertSame(actual, selected);
        assertSame(selected, result.orThrow());
        assertSame(actual, evaluated(result.attempts().getFirst()).observed());
        assertEquals(1, reads[0]);
    }

    @Test
    void returnsTheLastPersistenceResultAndBothPhases() {
        var time = new FakeTime(0);
        var results = List.of("acquired", "persisting", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedAwait(() -> results.get(calls[0]++),
                config(2, 10, 3), time, time).tryUntil(isNotNull);

        assertEquals("boundary", result.orThrow());
        assertEquals(List.of(AttemptRecord.Phase.ACQUISITION,
                        AttemptRecord.Phase.PERSISTENCE,
                        AttemptRecord.Phase.PERSISTENCE),
                result.attempts().stream().map(AttemptRecord::phase).toList());
    }

    @Test
    void retainsLegitimateNullAndSelectedOptionalValue() {
        var pollingTime = new FakeTime(0);
        AwaitResult<String, String> nullSource = await((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).tryUntil(isNull);
        AwaitResult<String, String> nullPredicate = await((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).tryUntil(matches(actual -> actual == null));
        AwaitResult<Optional<String>, String> selected =
                await(() -> Optional.of("payment")).usingTime(pollingTime, pollingTime).tryUntil(present);

        assertNull(nullSource.orThrow());
        assertNull(nullPredicate.orThrow());
        var observation = evaluated(nullSource.attempts().getFirst());
        assertInstanceOf(AttemptResult.Satisfied.class, observation.result());
        assertNull(observation.observed());
        assertEquals("payment", selected.orThrow());
    }

    @Test
    void everyExecutionStartsWithFreshHistory() {
        var time = new FakeTime(0);
        var stage = timedAwait(() -> "actual", config(1, 2, 0), time, time);

        var first = stage.tryUntil(isNotNull);
        var second = stage.tryUntil(isNotNull);

        assertEquals("actual", first.orThrow());
        assertEquals("actual", second.orThrow());

        assertEquals(List.of(1L), first.attempts().stream().map(AttemptRecord::number).toList());
        assertEquals(List.of(1L), second.attempts().stream().map(AttemptRecord::number).toList());
    }

}
