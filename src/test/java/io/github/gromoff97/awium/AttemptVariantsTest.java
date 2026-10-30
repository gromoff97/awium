package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.Attempt;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.isNull;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class AttemptVariantsTest {

    private final FakeTime time = new FakeTime(0);

    @Test
    void returnedNullRemainsAnObservationWhenTheConditionThrows() {
        var cause = new IllegalStateException("check failed");
        int[] reads = {0};
        Callable<String> source = () -> { reads[0]++; return null; };
        var result = tryAwait(source).usingTime(time, time).every(ofNanos(1))
                .until(captured(isNull, condition("broken", actual -> { throw cause; })));

        assertEquals(2, reads[0]);
        assertEquals(2L, result.conditions().getLast().attempts().lastKey());
        switch (result.conditions().getLast().attempts().lastEntry().getValue()) {
            case Attempt.FailedAfterObservation(var observed, var operation, var error, var location) -> {
                String actual = observed;
                assertNull(actual);
                assertSame(cause, error);
                assertEquals(Attempt.Operation.CONDITION, operation);
                assertNull(location);
            }
            default -> fail("expected a failure with the returned null observation");
        }
        assertSame(cause, failure(result).cause());
        assertThat(result.conditions().getLast().toString()).contains("Observed now: null");
    }

    @Test
    void sourceFailureHasNoObservationAndStopsAfterTheCompletedStage() {
        var cause = new IllegalStateException("source failed");
        int[] reads = {0};
        Callable<String> source = () -> {
            if (++reads[0] == 2) throw cause;
            return null;
        };
        var result = tryAwait(source).usingTime(time, time).every(ofNanos(1))
                .until(captured(isNull, isNull, isNull));

        assertEquals(2, reads[0]);
        assertInstanceOf(Attempt.Satisfied.class, result.conditions().getFirst().attempts().firstEntry().getValue());
        switch (result.conditions().get(1).attempts().lastEntry().getValue()) {
            case Attempt.FailedBeforeObservation(var operation, var error) -> {
                assertSame(cause, error);
                assertEquals(Attempt.Operation.SOURCE, operation);
            }
            default -> fail("expected a failure without an observation");
        }
        assertTrue(result.conditions().getLast().attempts().isEmpty());
        assertThat(result.conditions().get(1).toString()).contains("Observed: unavailable");
    }

    @Test
    void returnedMismatchWithACauseIsUnsatisfiedAndCanRetry() {
        var cause = new AssertionError("not ready");
        int[] checks = {0};
        var result = tryAwait((Callable<String>) () -> null).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", actual -> ++checks[0] == 1 ? unsatisfied("pending", cause) : satisfied()));

        assertNull(result.orThrow());
        assertEquals(2, checks[0]);
        switch (result.conditions().getFirst().attempts().firstEntry().getValue()) {
            case Attempt.Unsatisfied(var observed, var mismatch, var error) -> {
                String actual = observed;
                assertNull(actual);
                assertEquals("pending", mismatch);
                assertSame(cause, error);
            }
            default -> fail("a returned mismatch must remain a completed check");
        }
        assertInstanceOf(Attempt.Satisfied.class, result.conditions().getFirst().attempts().lastEntry().getValue());
    }
}
