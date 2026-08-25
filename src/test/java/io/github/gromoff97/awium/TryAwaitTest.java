package io.github.gromoff97.awium;

import io.github.gromoff97.awium.await.AwaitAttempt;
import io.github.gromoff97.awium.await.AwaitResult;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.await.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.await.TryAwait.tryAwait;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.yields;
import static io.github.gromoff97.awium.conditioning.conditions.ObjectCondition.isNotNull;
import static io.github.gromoff97.awium.conditioning.conditions.OptionalCondition.present;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class TryAwaitTest {

    @Test
    void returnsTheLastStableResultAndBothPhases() {
        var time = new FakeTime(0);
        var results = List.of("acquired", "stable", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedTryAwait(() -> "actual",
                config(2, 10, 3), time, time)
                .until(yields(actual -> results.get(calls[0]++)));

        var success = satisfied(result);
        assertEquals("boundary", success.result());
        assertEquals(List.of(AwaitAttempt.Phase.ACQUISITION,
                        AwaitAttempt.Phase.STABILIZATION,
                        AwaitAttempt.Phase.STABILIZATION),
                success.attempts().stream().map(AwaitAttempt::phase).toList());
    }

    @Test
    void retainsLegitimateNullAndSelectedOptionalValue() {
        AwaitResult<String, String> nullable = tryAwait((Source<String>) () -> "actual")
                .until(yields(actual -> null));
        AwaitResult<Optional<String>, String> selected = tryAwait(
                (Source.OptionalSource<String>) () -> Optional.of("payment"))
                .until(present);

        assertNull(satisfied(nullable).result());
        assertEquals("payment", satisfied(selected).result());
    }

    @Test
    void everyExecutionStartsWithFreshHistory() {
        var time = new FakeTime(0);
        var stage = timedTryAwait(() -> "actual", config(1, 2, 0), time, time);

        var first = satisfied(stage.until(isNotNull));
        var second = satisfied(stage.until(isNotNull));

        assertEquals(List.of(1L), first.attempts().stream().map(AwaitAttempt::number).toList());
        assertEquals(List.of(1L), second.attempts().stream().map(AwaitAttempt::number).toList());
    }

    private static WaitConfiguration config(long every, long upTo, long stableFor) {
        return new WaitConfiguration(every, upTo, stableFor);
    }

    @SuppressWarnings("unchecked")
    private static <S, R> AwaitResult.Satisfied<S, R> satisfied(AwaitResult<S, R> result) {
        return (AwaitResult.Satisfied<S, R>) assertInstanceOf(
                AwaitResult.Satisfied.class, result);
    }
}
