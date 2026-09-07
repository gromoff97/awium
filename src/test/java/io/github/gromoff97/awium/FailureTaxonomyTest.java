package io.github.gromoff97.awium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

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
        assertEquals(AwaitFailure.Reason.DIAGNOSTICS_FAILED, captured.reason());
        assertSame(rendering, captured.cause());
        assertSame(original, captured.suppressed().getFirst());
        var thrown = assertInstanceOf(AwaitExecutionException.class,
                assertFailure(AwaitFailure.Reason.DIAGNOSTICS_FAILED, () -> waiting.until(check)));
        assertSame(rendering, thrown.failure().cause());
        assertSame(original, thrown.failure().suppressed().getFirst());
        assertEquals(thrown.failure().message(), thrown.getMessage());
        assertSame(rendering, thrown.getCause());
        assertArrayEquals(new Throwable[]{original}, thrown.getSuppressed());
        assertEquals(0, original.getSuppressed().length);
        assertEquals(0, rendering.getSuppressed().length);
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
