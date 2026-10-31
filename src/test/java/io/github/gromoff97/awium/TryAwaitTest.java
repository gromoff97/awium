package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AwaitResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.Conditions.isNotNull;
import static io.github.gromoff97.awium.Conditions.isNull;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.OptionalConditions.present;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static io.github.gromoff97.awium.Conditions.instanceOf;
import static java.time.Duration.ofNanos;

class TryAwaitTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void ordinaryAwaitDoesNotCompareObservationsForHistory() {
        int[] comparisons = {0};
        class Observation {
            @Override public boolean equals(Object other) { comparisons[0]++; return true; }
            @Override public int hashCode() { return 0; }
        }
        var ready = new Observation();
        var observations = List.of(new Observation(), new Observation(), ready).iterator();

        var result = await(observations::next).usingTime(time, time).every(ofNanos(1))
                .until(matches(actual -> actual == ready));

        assertSame(ready, result);
        assertEquals(0, comparisons[0]);
    }

    @Test
    void narrowingKeepsTheOriginalObservationAndReturnsItsConcreteType() {
        var actual = new BigDecimal("42.00");
        int[] reads = {0};
        Callable<Number> source = () -> {
            reads[0]++;
            return actual;
        };

        AwaitResult<Number, BigDecimal> result = tryAwait(source).usingTime(time, time).until(instanceOf(BigDecimal.class));

        BigDecimal selected = result.orThrow();
        assertSame(actual, selected);
        assertSame(selected, result.orThrow());
        assertSame(actual, observation(attempts(result).getFirst()));
        assertEquals(1, reads[0]);
    }

    @Test
    void returnsTheLastPersistenceResultAndAllChangedObservations() {
        var results = List.of("acquired", "persisting", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedTryAwait(() -> results.get(calls[0]++),
                config(2, 10, 3), time, time).until(isNotNull);

        assertEquals("boundary", result.orThrow());
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 3L);
        assertThat(attempts(result)).extracting(AwaitTestAccess::observation).containsExactlyElementsOf(results);
    }

    @Test
    void retainsLegitimateNullAndSelectedOptionalValue() {
        AwaitResult<String, String> nullSource = tryAwait((Callable<String>) () -> null).usingTime(time, time).until(isNull);
        AwaitResult<String, String> nullPredicate = tryAwait((Callable<String>) () -> null).usingTime(time, time).until(matches(actual -> actual == null));
        AwaitResult<Optional<String>, String> selected =
                tryAwait(() -> Optional.of("payment")).usingTime(time, time).until(present);

        assertNull(nullSource.orThrow());
        assertNull(nullPredicate.orThrow());
        var observation = attempts(nullSource).getFirst();
        assertInstanceOf(Attempt.SatisfiedObservation.class, observation);
        assertNull(observation(observation));
        assertEquals("payment", selected.orThrow());
    }

    @Test
    void everyExecutionStartsWithFreshHistory() {
        var stage = timedAwait(() -> "actual", config(1, 2, 0), time, time);

        var first = diagnostic(stage).until(isNotNull);
        var second = diagnostic(stage).until(isNotNull);

        assertEquals("actual", first.orThrow());
        assertEquals("actual", second.orThrow());

        assertEquals(1, attempts(first).size());
        assertEquals(1, attempts(second).size());
    }

}
