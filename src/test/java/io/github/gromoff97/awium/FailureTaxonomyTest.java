package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.github.gromoff97.awium.ConditionTestRuntime.diagnosticCondition;
import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Conditions.asserted;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class FailureTaxonomyTest {

    static Throwable assertFailure(AwaitFailure.Reason reason, Executable executable) {
        Throwable thrown = switch (reason) {
            case TIMEOUT, PERSISTENCE_FAILED -> assertThrows(AwaitAssertionError.class, executable);
            default -> assertThrows(AwaitExecutionException.class, executable);
        };
        AwaitFailure failure = thrown instanceof AwaitAssertionError assertion
                ? assertion.failure() : ((AwaitExecutionException) thrown).failure();
        assertEquals(reason, failure.reason());
        return thrown;
    }

    @Test
    void diagnosticFailureRetainsBothOriginalCausesWithoutMutatingThem() {
        var time = new FakeTime(0);
        var original = new AssertionError("not ready");
        var rendering = new IllegalStateException("formatting failed");
        var actual = new Object() {
            @Override public String toString() { throw rendering; }
        };
        var waiting = await(() -> actual).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2));
        var check = asserted(value -> { throw original; });

        var captured = assertInstanceOf(AwaitResult.Failed.class, waiting.tryUntil(check)).failure();
        assertEquals(AwaitFailure.Reason.TIMEOUT, captured.reason());
        assertSame(original, captured.cause());
        assertSame(rendering, captured.suppressed().getFirst());
        var thrown = assertInstanceOf(AwaitAssertionError.class,
                assertFailure(AwaitFailure.Reason.TIMEOUT, () -> waiting.until(check)));
        assertSame(original, thrown.failure().cause());
        assertSame(rendering, thrown.failure().suppressed().getFirst());
        assertEquals(thrown.failure().message(), thrown.getMessage());
        assertSame(original, thrown.getCause());
        assertArrayEquals(new Throwable[]{rendering}, thrown.getSuppressed());
        assertEquals(0, original.getSuppressed().length);
        assertEquals(0, rendering.getSuppressed().length);
    }

    @Test
    void renderingFailurePreservesEveryOriginalFailureCategory() {
        for (var reason : AwaitFailure.Reason.values()) {
            var rendering = new IllegalStateException("formatting failed");
            Object expected = new Object() {
                @Override public String toString() { throw rendering; }
            };
            Throwable original = switch (reason) {
                case TIMEOUT, PERSISTENCE_FAILED -> new AssertionError("not ready");
                case INTERRUPTED -> new InterruptedException("interrupted");
                default -> new IllegalStateException("operation failed");
            };
            var timing = new AttemptRecord.Timing.AfterObservation(Duration.ZERO,
                    Duration.ZERO, Duration.ZERO, ofNanos(2));
            var metadata = diagnosticCondition("ready", null, new Condition.Reference<>("Expected", expected));
            AttemptRecord.Outcome<Object, Object> observation = switch (reason) {
                case WAIT_FAILED -> new AttemptRecord.Outcome.WaitingFailed<>(
                        new AttemptRecord.Timing.BeforeRetrieval(Duration.ZERO, ofNanos(2)), original);
                case SOURCE_FAILED -> new AttemptRecord.Outcome.SourceRetrievalFailed<>(
                        new AttemptRecord.Timing.BeforeObservation(Duration.ZERO, Duration.ZERO, ofNanos(2)), original);
                case TIMEOUT, PERSISTENCE_FAILED -> new AttemptRecord.Outcome.Evaluated<>(timing, "actual",
                        ConditionResult.assertionUnsatisfied("not ready", (AssertionError) original), metadata, null);
                default -> new AttemptRecord.Outcome.ConditionFailed<>(timing, "actual", original, metadata, null);
            };
            var attempt = new AttemptRecord<>(1, reason == AwaitFailure.Reason.PERSISTENCE_FAILED
                    ? AttemptRecord.Phase.PERSISTENCE : AttemptRecord.Phase.ACQUISITION, observation);
            WaitCompletion<Object, Object> outcome = switch (reason) {
                case TIMEOUT -> new WaitCompletion.LateTimeout<>(attempt);
                case PERSISTENCE_FAILED -> new WaitCompletion.PersistenceFailure<>(0, attempt);
                default -> new WaitCompletion.Uncontrolled<>(attempt);
            };
            var configuration = AwaitTestAccess.config(1, 2, 0);
            try {
                var captured = FailureDiagnostics.failure(outcome, metadata, configuration);
                assertEquals(reason, captured.reason());
                assertSame(original, captured.cause());
                assertEquals(List.of(rendering), captured.suppressed());
                var thrown = assertFailure(reason, () -> Await.complete(outcome, metadata, configuration));
                assertSame(original, thrown.getCause());
                assertArrayEquals(new Throwable[]{rendering}, thrown.getSuppressed());
                assertEquals(0, original.getSuppressed().length);
                assertEquals(0, rendering.getSuppressed().length);
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void causeThatFailsToDescribeItselfIsNotDuplicatedAsSuppressed() {
        var original = new IllegalStateException() {
            @Override public String getMessage() { throw this; }
        };
        var failure = assertFailure(AwaitFailure.Reason.CONDITION_FAILED,
                () -> await(() -> "actual").until(Conditions.condition("ready", value -> { throw original; })));
        assertSame(original, failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(0, original.getSuppressed().length);
    }

    @Test
    void structuredFailureAllowsNoCauseAndDefensivelyCopiesSuppressedCauses() {
        var causes = new ArrayList<Throwable>(List.of(new AssertionError("original")));
        var failure = new AwaitFailure(AwaitFailure.Reason.TIMEOUT, "not ready", null, causes);
        causes.clear();
        assertNull(failure.cause());
        assertEquals(1, failure.suppressed().size());
        assertThrows(UnsupportedOperationException.class, () -> failure.suppressed().clear());
        assertThrows(NullPointerException.class, () -> new AwaitFailure(null, "message", null, List.of()));
        assertThrows(NullPointerException.class, () -> new AwaitFailure(AwaitFailure.Reason.TIMEOUT, null, null, List.of()));
    }
}
