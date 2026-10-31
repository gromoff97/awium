package io.github.gromoff97.awium.result;

import io.github.gromoff97.awium.support.FakeTime;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.AwaitTestAccess.timedAwait;
import static io.github.gromoff97.awium.support.TestConditions.check;
import static io.github.gromoff97.awium.support.TestConditions.expected;
import static java.time.Duration.ofNanos;
import static org.apache.commons.lang3.SerializationUtils.roundtrip;
import static org.junit.jupiter.api.Assertions.*;

class FailureSerializationTest {

    @Test
    void exceptionsKeepTheirFailureMetadataThroughJavaSerialization() {
        var time = new FakeTime(0);
        var display = new BrokenDisplay();
        var assertion = new AssertionError("not ready");
        var timeout = assertThrows(AwaitAssertionError.class,
                () -> timedAwait(() -> display, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(check("not ready", value -> io.github.gromoff97.awium.result.ConditionResult.unsatisfied("not ready", assertion))));
        Callable<BrokenDisplay> source = () -> { throw new IOException("source failed"); };
        var execution = assertThrows(AwaitExecutionException.class,
                () -> await(source).until(expected(display)));

        for (Throwable original : List.of(timeout, execution)) {
            var restored = roundtrip(original);
            assertEquals(original.getClass(), restored.getClass());
            assertEquals(original.getMessage(), restored.getMessage());
            assertArrayEquals(original.getStackTrace(), restored.getStackTrace());
            assertEquals(original.getCause().getClass(), restored.getCause().getClass());
            assertEquals(original.getCause().getMessage(), restored.getCause().getMessage());
            assertEquals(1, restored.getSuppressed().length);
            assertEquals(original.getSuppressed()[0].getClass(), restored.getSuppressed()[0].getClass());
            assertEquals(original.getSuppressed()[0].getMessage(), restored.getSuppressed()[0].getMessage());

            var metadata = failure(restored);
            assertEquals(failure(original).reason(), metadata.reason());
            assertEquals(restored.getMessage(), metadata.message());
            assertSame(restored.getCause(), metadata.cause());
            assertArrayEquals(restored.getSuppressed(), metadata.suppressed().toArray(Throwable[]::new));
            assertThrows(UnsupportedOperationException.class, () -> metadata.suppressed().clear());
        }
    }

    private static AwaitFailure failure(Throwable exception) {
        return exception instanceof AwaitAssertionError assertion
                ? assertion.failure() : ((AwaitExecutionException) exception).failure();
    }

    private static final class BrokenDisplay {
        @Override public String toString() {
            throw new IllegalStateException("display unavailable");
        }
    }
}
