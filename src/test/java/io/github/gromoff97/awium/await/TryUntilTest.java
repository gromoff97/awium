package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.FakeTime;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.results.AwaitAttempt;
import io.github.gromoff97.awium.results.AwaitResult;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.await.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.await.Await.await;
import static io.github.gromoff97.awium.conditions.Conditions.yields;
import static io.github.gromoff97.awium.conditions.Conditions.isNotNull;
import static io.github.gromoff97.awium.conditions.Conditions.isNull;
import static io.github.gromoff97.awium.conditions.OptionalConditions.present;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class TryUntilTest {

    @Test
    void returnsTheLastPersistenceResultAndBothPhases() {
        var time = new FakeTime(0);
        var results = List.of("acquired", "persisting", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedAwait(() -> "actual",
                config(2, 10, 3), time, time).tryUntil(yields(actual -> results.get(calls[0]++)));

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
        AwaitResult<String, String> nullable = await((Source<String>) () -> "actual").usingTime(pollingTime, pollingTime).tryUntil(yields(actual -> null));
        AwaitResult<String, Void> nullSource = await((Source<String>) () -> null).usingTime(pollingTime, pollingTime).tryUntil(isNull);
        AwaitResult<Optional<String>, String> selected =
                await((Source.OptionalSource<String>) () -> Optional.of("payment")).usingTime(pollingTime, pollingTime).tryUntil(present);

        assertNull(satisfied(nullable).result());
        assertNull(satisfied(nullSource).result());
        var outcome = assertInstanceOf(AwaitAttempt.Outcome.Satisfied.class,
                satisfied(nullSource).attempts().getFirst().outcome());
        assertNull(outcome.observed());
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

    private static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }

    @SuppressWarnings("unchecked")
    private static <S, R> AwaitResult.Satisfied<S, R> satisfied(AwaitResult<S, R> result) {
        return (AwaitResult.Satisfied<S, R>) assertInstanceOf(
                AwaitResult.Satisfied.class, result);
    }
}
