package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AwaitResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.tryAwait;
import static io.github.gromoff97.awium.Conditions.captured;
import static io.github.gromoff97.awium.Conditions.condition;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryFormattingTest {

    @Test
    void repeatedGroupRendersSharedDiagnosticsOnceAndBothObservationBoundaries() {
        var time = new FakeTime(0);
        int[] calls = {0};
        var result = tryAwait(() -> ++calls[0]).usingTime(time, time).every(ofNanos(1))
                .until(equalTo(4).because("worker must finish"));

        assertEquals("""
                Attempts 1–3 (ACQUISITION)
                    Completed after: 0 nanoseconds → 2 nanoseconds
                    Condition: value equals expected
                        Expected: 4
                        Because: worker must finish
                    Result: unsatisfied
                    Mismatch: value was not equal
                    First observed: 1
                    Last observed: 3""", result.attempts().getFirst().toString());
        assertEquals("""
                Attempt 4 (ACQUISITION)
                    Completed after: 3 nanoseconds
                    Condition: value equals expected
                        Expected: 4
                        Because: worker must finish
                    Result: satisfied
                    Observed: 4""", result.attempts().getLast().toString());
        assertEquals(4, calls[0]);
    }

    @Test
    void capturedHistoryExplainsTheSequenceAndTheAttemptedStage() {
        var time = new FakeTime(0);
        var values = List.of("created", "pending", "pending", "paid").iterator();
        var result = tryAwait(values::next).usingTime(time, time).every(ofNanos(1))
                .until(captured(equalTo("created"), equalTo("paid").because("payment accepted"))
                        .because("order lifecycle"));

        assertEquals("""
                Attempts 2–3 (ACQUISITION)
                    Completed after: 1 nanosecond → 2 nanoseconds
                    Stage: 2 of 2 (captured 1)
                    Sequence because: order lifecycle
                    Condition: value equals expected
                        Expected: paid
                        Because: payment accepted
                    Result: unsatisfied
                    Mismatch: value was not equal
                    First observed: pending
                    Last observed: pending""", result.attempts().get(1).toString());
    }

    @Test
    void aReturnedNullIsDifferentFromAnUnavailableObservation() {
        var time = new FakeTime(0);
        var returnedNull = tryAwait((java.util.concurrent.Callable<String>) () -> null)
                .usingTime(time, time).until(Conditions.isNull);
        var cause = new IllegalStateException("source unavailable");
        var sourceFailed = tryAwait((java.util.concurrent.Callable<String>) () -> { throw cause; })
                .usingTime(time, time).until(Conditions.isNull);

        assertTrue(returnedNull.attempts().getFirst().toString().contains("Observed: null"));
        String failed = sourceFailed.attempts().getFirst().toString();
        assertFalse(failed.contains("Observed:"), failed);
        assertTrue(failed.contains("Result: uncontrolled (SOURCE)"), failed);
        assertTrue(failed.contains("Cause: java.lang.IllegalStateException"), failed);
        assertTrue(failed.contains("Message: source unavailable"), failed);
        assertSame(cause, assertInstanceOf(AwaitResult.Failed.class, sourceFailed).failure().cause());
    }

    @Test
    void valueRenderingIsDeferredAndFailuresCannotReplaceTheWaitOutcome() {
        var time = new FakeTime(0);
        int[] renders = {0};
        Object observed = new Object() {
            @Override public String toString() {
                renders[0]++;
                throw new IllegalArgumentException("unprintable");
            }
        };
        var result = tryAwait(() -> observed).usingTime(time, time)
                .until(condition("ready", actual -> satisfied()));

        assertEquals(0, renders[0]);
        assertSame(observed, result.orThrow());
        String rendered = result.attempts().getFirst().toString();
        assertEquals(1, renders[0]);
        assertTrue(rendered.contains("Observed: <value unavailable: diagnostics failed>"), rendered);
        assertTrue(rendered.contains("Diagnostics failure: java.lang.IllegalArgumentException"), rendered);
        assertSame(observed, result.orThrow());
    }

    @Test
    void multilineReasonsAndArrayValuesRemainReadable() {
        var time = new FakeTime(0);
        var result = tryAwait(() -> new int[]{1, 2}).usingTime(time, time)
                .until(equalTo(new int[]{1, 2}).because("first line\r\nsecond line"));

        String rendered = result.attempts().getFirst().toString();
        assertTrue(rendered.contains("Expected: [1, 2]"), rendered);
        assertTrue(rendered.contains("Because:\n            first line\n            second line"), rendered);
        assertTrue(rendered.contains("Observed: [1, 2]"), rendered);
    }
}
