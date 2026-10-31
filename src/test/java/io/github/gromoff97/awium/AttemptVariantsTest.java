package io.github.gromoff97.awium;

import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.AwaitTestAccess.failure;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.isNull;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.Attempt.Operation.SOURCE;
import static io.github.gromoff97.awium.result.Attempt.Satisfied;
import static io.github.gromoff97.awium.result.Attempt.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.time.Duration.ofNanos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            case FailedAfterObservation(var observed, var operation, var error, var location) -> {
                String actual = observed;
                assertNull(actual);
                assertSame(cause, error);
                assertEquals(CONDITION, operation);
                assertNull(location);
            }
            default -> fail("expected a failure with the returned null observation");
        }
        assertSame(cause, failure(result).cause());
        assertThat(result.conditions().getLast().toString()).contains("Observed now: null");
    }

    @Test
    void sourceFailureHasNoObservationAndStopsAfterTheCompletedStage() throws Exception {
        var cause = new IllegalStateException("source failed");
        Callable<String> source = mock();
        when(source.call()).thenReturn(null).thenAnswer(ignored -> { throw cause; });
        var result = tryAwait(source).usingTime(time, time).every(ofNanos(1))
                .until(captured(isNull, isNull, isNull));

        verify(source, times(2)).call();
        assertInstanceOf(Satisfied.class, result.conditions().getFirst().attempts().firstEntry().getValue());
        switch (result.conditions().get(1).attempts().lastEntry().getValue()) {
            case FailedBeforeObservation(var operation, var error) -> {
                assertSame(cause, error);
                assertEquals(SOURCE, operation);
            }
            default -> fail("expected a failure without an observation");
        }
        assertTrue(result.conditions().getLast().attempts().isEmpty());
        assertThat(result.conditions().get(1).toString()).contains("Observed: unavailable");
    }

    @Test
    void returnedMismatchWithACauseIsUnsatisfiedAndCanRetry() {
        var cause = new AssertionError("not ready");
        var outcomes = List.of(unsatisfied("pending", cause), satisfied()).iterator();
        var result = tryAwait((Callable<String>) () -> null).usingTime(time, time).every(ofNanos(1))
                .until(condition("ready", actual -> outcomes.next()));

        assertNull(result.orThrow());
        assertFalse(outcomes.hasNext());
        switch (result.conditions().getFirst().attempts().firstEntry().getValue()) {
            case Unsatisfied(var observed, var mismatch, var error) -> {
                String actual = observed;
                assertNull(actual);
                assertEquals("pending", mismatch);
                assertSame(cause, error);
            }
            default -> fail("a returned mismatch must remain a completed check");
        }
        assertInstanceOf(Satisfied.class, result.conditions().getFirst().attempts().lastEntry().getValue());
    }
}
