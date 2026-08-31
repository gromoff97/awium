package io.github.gromoff97.awium.condition;

import io.github.gromoff97.awium.exceptions.AwaitConfigurationConflictException;

import java.time.Duration;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

record WaitConfiguration(long everyNanos, long upToNanos, long persistenceNanos) {

    private static final long[] UNIT_NANOS = {86_400_000_000_000L, 3_600_000_000_000L, 60_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L, 1L};
    private static final String[] UNIT_NAMES = {"day", "hour", "minute", "second", "millisecond", "microsecond", "nanosecond"};

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

    void validatePair() {
        if (everyNanos >= upToNanos) {
            throw new AwaitConfigurationConflictException("polling interval (" + duration(everyNanos)
                    + ") must be shorter than acquisition timeout (" + duration(upToNanos) + ")");
        }
    }

    static String duration(long nanos) {
        StringJoiner result = new StringJoiner(" ");
        for (int index = 0; index < UNIT_NANOS.length; index++) {
            long count = nanos / UNIT_NANOS[index];
            nanos %= UNIT_NANOS[index];
            if (count != 0) {
                result.add(count + " " + UNIT_NAMES[index] + (count == 1 ? "" : "s"));
            }
        }
        return result.length() == 0 ? "0 nanoseconds" : result.toString();
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
