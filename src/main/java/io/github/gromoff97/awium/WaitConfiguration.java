package io.github.gromoff97.awium;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

record WaitConfiguration(long everyNanos, long upToNanos, long persistenceNanos) {

    WaitConfiguration {
        requirePositive(everyNanos, "polling interval");
        requirePositive(upToNanos, "acquisition timeout");
        if (persistenceNanos < 0) {
            throw new IllegalArgumentException("persistence duration must be non-negative");
        }
    }

    static WaitConfiguration defaults() {
        return new WaitConfiguration(Duration.ofMillis(100).toNanos(), Duration.ofSeconds(10).toNanos(), 0L);
    }

    WaitConfiguration withEvery(Duration value) {
        return new WaitConfiguration(nanos(value, "polling interval"), upToNanos, persistenceNanos);
    }

    WaitConfiguration withUpTo(Duration value) {
        return new WaitConfiguration(everyNanos, nanos(value, "acquisition timeout"), persistenceNanos);
    }

    WaitConfiguration withPersistence(Duration value) {
        return new WaitConfiguration(everyNanos, upToNanos, nanos(value, "persistence duration"));
    }

    private static void requirePositive(long nanos, String label) {
        if (nanos <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static long nanos(Duration value, String label) {
        try {
            return requireNonNull(value, label + " must not be null").toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(label + " exceeds the supported nanosecond range", overflow);
        }
    }
}
