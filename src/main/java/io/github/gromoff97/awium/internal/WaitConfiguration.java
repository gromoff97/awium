package io.github.gromoff97.awium.internal;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

public record WaitConfiguration(long everyNanos, long upToNanos, long persistenceNanos,
        LongSupplier clock, LongConsumer parker) {

    public static final WaitConfiguration DEFAULT = new WaitConfiguration(
            ofMillis(100).toNanos(), ofSeconds(10).toNanos(), 0L,
            System::nanoTime, LockSupport::parkNanos);

    public WaitConfiguration {
        requireNonNull(clock, "clock must not be null");
        requireNonNull(parker, "parker must not be null");
        requirePositive(everyNanos, "polling interval");
        requirePositive(upToNanos, "acquisition timeout");
        if (persistenceNanos < 0) {
            throw new IllegalArgumentException("persistence duration must be non-negative");
        }
    }

    public WaitConfiguration withEvery(Duration value) {
        return new WaitConfiguration(nanos(value, "polling interval"), upToNanos, persistenceNanos, clock, parker);
    }

    public WaitConfiguration withUpTo(Duration value) {
        return new WaitConfiguration(everyNanos, nanos(value, "acquisition timeout"), persistenceNanos, clock, parker);
    }

    public WaitConfiguration withPersistence(Duration value) {
        return new WaitConfiguration(everyNanos, upToNanos, nanos(value, "persistence duration"), clock, parker);
    }

    public WaitConfiguration withTime(LongSupplier clock, LongConsumer parker) {
        return new WaitConfiguration(everyNanos, upToNanos, persistenceNanos, clock, parker);
    }

    private static void requirePositive(long nanos, String label) {
        if (nanos <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static long nanos(Duration value, String label) {
        requireNonNull(value, label + " must not be null");
        return value.toNanos();
    }
}
