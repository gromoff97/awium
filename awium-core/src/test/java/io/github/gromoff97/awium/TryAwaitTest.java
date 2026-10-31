package io.github.gromoff97.awium;

import io.github.gromoff97.awium.support.FakeTime;
import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.attempts;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.observation;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.FailureTaxonomyTest.assertFailure;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.matching;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
                .until(matching(actual -> actual == ready));

        assertSame(ready, result);
        assertEquals(0, comparisons[0]);
    }

    @Test
    void returnsTheLastPersistenceResultAndAllChangedObservations() {
        var results = List.of("acquired", "persisting", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedTryAwait(() -> results.get(calls[0]++),
                config(2, 10, 3), time, time).until(accepted);

        assertEquals("boundary", result.orThrow());
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 3L);
        assertThat(attempts(result)).extracting(AwaitTestAccess::observation).containsExactlyElementsOf(results);
    }

    @Test
    void everyExecutionStartsWithFreshHistory() {
        var stage = timedAwait(() -> "actual", config(1, 2, 0), time, time);

        var first = diagnostic(stage).until(accepted);
        var second = diagnostic(stage).until(accepted);

        assertEquals("actual", first.orThrow());
        assertEquals("actual", second.orThrow());

        assertEquals(1, attempts(first).size());
        assertEquals(1, attempts(second).size());
    }

    @Test
    void returnedMismatchWithACauseIsUnsatisfiedAndCanRetry() {
        var cause = new AssertionError("not ready");
        var outcomes = List.of(unsatisfied("pending", cause), satisfied(null)).iterator();
        var result = tryAwait((Callable<String>) () -> null).usingTime(time, time).every(ofNanos(1))
                .until(check("ready", actual -> outcomes.next()));

        assertNull(result.orThrow());
        assertFalse(outcomes.hasNext());
        switch (result.conditions().getFirst().attempts().firstEntry().getValue()) {
            case UnsatisfiedObservation(var observed, var mismatch, var error) -> {
                String actual = observed;
                assertNull(actual);
                assertEquals("pending", mismatch);
                assertSame(cause, error);
            }
            default -> fail("a returned mismatch must remain a completed check");
        }
        assertInstanceOf(SatisfiedObservation.class, result.conditions().getFirst().attempts().lastEntry().getValue());
    }

    @Test
    void reusableStageRetainsTheExactSourceAndStartsEachTerminalFresh() {
        int[] calls = {0};
        ValueAwait<Integer> stage = timedAwait(() -> ++calls[0], config(1, 3, 0), time, time);
        PreservingCondition<Integer> evenObservation = matching(value -> value % 2 == 0);

        assertEquals(2, stage.until(evenObservation));
        assertEquals(4, stage.until(evenObservation));
        assertEquals(4, calls[0]);
    }

    @Test
    void reusableStageStartsFreshAfterControlledAndUncontrolledFailures() {
        int[] sourceCalls = {0};
        ValueAwait<String> stage = timedAwait(() -> {
            sourceCalls[0]++;
            return "value";
        }, config(1, 3, 0), time, time);
        Condition<String, String> never = check(
                "never", value -> unsatisfied("not yet"));
        var failure = new IllegalStateException("condition failed");
        Condition<String, String> broken = check(
                "broken", value -> {
                    throw failure;
                });

        assertFailure(TIMEOUT, () -> stage.until(never));
        assertSame(failure, assertFailure(CONDITION_FAILED,
                () -> stage.until(broken)).getCause());

        assertEquals("value", stage.until(check(
                "ready", ignored -> satisfied(ignored))));
        assertEquals(5, sourceCalls[0]);
    }
}
