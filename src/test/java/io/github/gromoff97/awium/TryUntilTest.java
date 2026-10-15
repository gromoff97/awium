package io.github.gromoff97.awium;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
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
        Source<Number> source = () -> {
            reads[0]++;
            return actual;
        };
        var time = new FakeTime(0);

        AwaitResult<Number, BigDecimal> result = await(source).usingTime(time, time).tryUntil(Conditions.instanceOf(BigDecimal.class));

        assertSame(actual, satisfied(result).result());
        assertSame(actual, assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, result.attempts().getFirst().outcome()).observed());
        assertEquals(1, reads[0]);
    }

    @Test
    void returnsTheLastPersistenceResultAndBothPhases() {
        var time = new FakeTime(0);
        var results = List.of("acquired", "persisting", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedAwait(() -> results.get(calls[0]++),
                config(2, 10, 3), time, time).tryUntil(isNotNull);

        var success = satisfied(result);
        assertEquals("boundary", success.result());
        assertEquals(List.of(AwaitAttempt.Phase.ACQUISITION,
                        AwaitAttempt.Phase.PERSISTENCE,
                        AwaitAttempt.Phase.PERSISTENCE),
                success.attempts().stream().map(AwaitAttempt::phase).toList());
    }

    @Test
    void retainsLegitimateNullAndSelectedOptionalValue() {
        var pollingTime = new FakeTime(0);
        AwaitResult<String, String> nullable = await((Source<String>) () -> null).usingTime(pollingTime, pollingTime).tryUntil(matches(actual -> true));
        AwaitResult<String, Void> nullSource = await((Source<String>) () -> null).usingTime(pollingTime, pollingTime).tryUntil(isNull);
        AwaitResult<String, String> nullPredicate = await((Source<String>) () -> null).usingTime(pollingTime, pollingTime).tryUntil(matches(actual -> actual == null));
        AwaitResult<Optional<String>, String> selected =
                await((Source.OptionalSource<String>) () -> Optional.of("payment")).usingTime(pollingTime, pollingTime).tryUntil(present);

        assertNull(satisfied(nullable).result());
        assertNull(satisfied(nullSource).result());
        assertNull(satisfied(nullPredicate).result());
        assertInstanceOf(ConditionResult.Satisfied.class,
                assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, satisfied(nullSource).attempts().getFirst().outcome()).evaluation());
        assertNull(assertInstanceOf(AwaitAttempt.Outcome.Evaluated.class, satisfied(nullSource).attempts().getFirst().outcome()).observed());
        assertEquals("payment", satisfied(selected).result());
    }

    @Test
    void everyExecutionStartsWithFreshHistory() {
        var time = new FakeTime(0);
        var stage = timedAwait(() -> "actual", config(1, 2, 0), time, time);

        var first = satisfied(stage.tryUntil(isNotNull));
        var second = satisfied(stage.tryUntil(isNotNull));

        assertEquals(List.of(1L), first.attempts().stream().map(AwaitAttempt::number).toList());
        assertEquals(List.of(1L), second.attempts().stream().map(AwaitAttempt::number).toList());
    }

    @SuppressWarnings("unchecked")
    private static <S, R> AwaitResult.Satisfied<S, R> satisfied(AwaitResult<S, R> result) {
        return (AwaitResult.Satisfied<S, R>) assertInstanceOf(
                AwaitResult.Satisfied.class, result);
    }
}
