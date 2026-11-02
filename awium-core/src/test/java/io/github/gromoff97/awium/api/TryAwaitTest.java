package io.github.gromoff97.awium.api;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.ValueAwait;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.AwaitAssertionError;
import io.github.gromoff97.awium.result.AwaitExecutionException;
import io.github.gromoff97.awium.support.AwaitAssertions;
import io.github.gromoff97.awium.support.FakeTime;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.AwaitTestAccess.config;
import static io.github.gromoff97.awium.AwaitTestAccess.diagnostic;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.timedTryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.withTime;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.CONDITION_FAILED;
import static io.github.gromoff97.awium.result.AwaitFailure.Reason.TIMEOUT;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static io.github.gromoff97.awium.support.AwaitAssertions.assertFailure;
import static io.github.gromoff97.awium.support.AwaitAssertions.attempts;
import static io.github.gromoff97.awium.support.AwaitAssertions.observation;
import static io.github.gromoff97.awium.support.TestConditions.accepted;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.matching;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TryAwaitTest {

    private final FakeTime time = new FakeTime(0);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothTerminalsGroupObservationsAndReturnTheSelectedValue(boolean returnResult) {
        int[] comparisons = {0};
        class Observation {
            @Override public boolean equals(Object other) { comparisons[0]++; return true; }
            @Override public int hashCode() { return 0; }
        }
        var ready = new Observation();
        var observations = List.of(new Observation(), new Observation(), ready).iterator();

        var waiting = withTime(await(observations::next), time, time).every(ofNanos(1));
        var condition = matching((Observation actual) -> actual == ready);
        var result = returnResult ? diagnostic(waiting).until(condition).orThrow() : waiting.until(condition);

        assertSame(ready, result);
        assertEquals(1, comparisons[0]);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void timeoutShowsEarlierReasonsIncludingOneThatReturns(boolean returnResult) {
        var reasons = List.of("payment pending", "payment pending", "stock missing", "payment pending", "payment pending").iterator();
        var condition = check("order ready", actual -> unsatisfied(reasons.next()));
        var waiting = timedAwait(() -> "order", config(1, 5, 0), time, time);
        AwaitAssertionError error;
        if (returnResult) {
            var result = diagnostic(waiting).until(condition);
            error = assertThrows(AwaitAssertionError.class, result::orThrow);
            assertThat(error.getMessage()).isEqualTo(((AwaitResult.Failed<?, ?>) result).failure().message());
        } else {
            error = assertThrows(AwaitAssertionError.class, () -> waiting.until(condition));
        }

        assertThat(error.getMessage()).contains("""
                Through #2: unsatisfied
                    Mismatch: payment pending
                    Observed now: "order"

                Through #3: unsatisfied
                    Mismatch: stock missing
                    Observed now: "order"

                Through #5: unsatisfied
                    Mismatch: payment pending
                    Observed now: "order"
                """.stripTrailing());
        assertFalse(reasons.hasNext());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anExecutionFailureKeepsEarlierRejections(boolean returnResult) {
        var cause = new IllegalStateException("source unavailable");
        int[] calls = {0};
        var waiting = timedAwait(() -> {
            if (++calls[0] == 3) throw cause;
            return "order";
        }, config(1, 5, 0), time, time);
        var condition = check("order ready", actual -> unsatisfied("payment pending"));
        var error = assertThrows(AwaitExecutionException.class, () -> {
            if (returnResult) diagnostic(waiting).until(condition).orThrow();
            else waiting.until(condition);
        });

        assertSame(cause, error.getCause());
        assertThat(error.getMessage()).contains("Through #2: unsatisfied", "source unavailable")
                .containsOnlyOnce("Mismatch: payment pending");
    }

    @Test
    void returnsTheLastPersistenceResultAndAllChangedObservations() {
        var results = List.of("acquired", "persisting", "boundary");
        int[] calls = {0};

        AwaitResult<String, String> result = timedTryAwait(() -> results.get(calls[0]++),
                config(2, 10, 3), time, time).until(accepted);

        assertEquals("boundary", result.orThrow());
        assertThat(result.conditions().getFirst().attempts().keySet()).containsExactly(1L, 2L, 3L);
        assertThat(attempts(result)).extracting(AwaitAssertions::observation).containsExactlyElementsOf(results);
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
        var result = withTime(tryAwait((Callable<String>) () -> null), time, time).every(ofNanos(1))
                .until(check("ready", actual -> outcomes.next()));

        assertNull(result.orThrow());
        assertFalse(outcomes.hasNext());
        switch (result.conditions().getFirst().attempts().firstEntry().getValue()) {
            case UnsatisfiedObservation(var observed, var diagnostic, var error) -> {
                String actual = observed;
                assertNull(actual);
                assertEquals("pending", diagnostic.mismatch());
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
