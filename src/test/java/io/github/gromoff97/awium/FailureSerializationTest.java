package io.github.gromoff97.awium;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.Await.await;
import static io.github.gromoff97.awium.Conditions.asserted;
import static io.github.gromoff97.awium.Conditions.equalTo;
import static java.time.Duration.ofNanos;
import static org.junit.jupiter.api.Assertions.*;

class FailureSerializationTest {

    @Test
    void exceptionsKeepTheirFailureMetadataThroughJavaSerialization() throws Exception {
        var time = new FakeTime(0);
        var display = new BrokenDisplay();
        var assertion = new AssertionError("not ready");
        var timeout = assertThrows(AwaitAssertionError.class,
                () -> await(() -> display).usingTime(time, time).every(ofNanos(1)).upTo(ofNanos(2))
                        .until(asserted(value -> { throw assertion; })));
        Callable<BrokenDisplay> source = () -> { throw new IOException("source failed"); };
        var execution = assertThrows(AwaitExecutionException.class,
                () -> await(source).until(equalTo(display)));

        for (Throwable original : List.of(timeout, execution)) {
            var restored = roundTrip(original);
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

    private static Throwable roundTrip(Throwable failure) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(failure);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Throwable) input.readObject();
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
