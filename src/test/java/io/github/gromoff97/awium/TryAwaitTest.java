package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.HistoryEntry;
import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
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

class TryAwaitTest {

    @Test
    void narrowingKeepsTheOriginalObservationAndReturnsItsConcreteType() {
        var actual = new BigDecimal("42.00");
        int[] reads = {0};
        Callable<Number> source = () -> {
            reads[0]++;
            return actual;
        };
        var time = new FakeTime(0);

        AwaitResult<Number, BigDecimal> result = tryAwait(source).usingTime(time, time).until(Conditions.instanceOf(BigDecimal.class));

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

        AwaitResult<String, String> result = timedTryAwait(() -> results.get(calls[0]++),
                config(2, 10, 3), time, time).until(isNotNull);

        assertEquals("boundary", result.orThrow());
        assertEquals(List.of(HistoryEntry.Phase.ACQUISITION,
                        HistoryEntry.Phase.PERSISTENCE,
                        HistoryEntry.Phase.PERSISTENCE),
                result.attempts().stream().map(HistoryEntry::phase).toList());
    }

    @Test
    void retainsLegitimateNullAndSelectedOptionalValue() {
        var pollingTime = new FakeTime(0);
        AwaitResult<String, String> nullSource = tryAwait((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).until(isNull);
        AwaitResult<String, String> nullPredicate = tryAwait((Callable<String>) () -> null).usingTime(pollingTime, pollingTime).until(matches(actual -> actual == null));
        AwaitResult<Optional<String>, String> selected =
                tryAwait(() -> Optional.of("payment")).usingTime(pollingTime, pollingTime).until(present);

        assertNull(nullSource.orThrow());
        assertNull(nullPredicate.orThrow());
        var observation = evaluated(nullSource.attempts().getFirst());
        assertInstanceOf(AttemptStatus.Satisfied.class, observation.result());
        assertNull(observation.observed());
        assertEquals("payment", selected.orThrow());
    }

    @Test
    void everyExecutionStartsWithFreshHistory() {
        var time = new FakeTime(0);
        var stage = timedAwait(() -> "actual", config(1, 2, 0), time, time);

        var first = diagnostic(stage).until(isNotNull);
        var second = diagnostic(stage).until(isNotNull);

        assertEquals("actual", first.orThrow());
        assertEquals("actual", second.orThrow());

        assertEquals(1, first.attempts().size());
        assertEquals(1, second.attempts().size());
    }

}
