package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exception.AwaitConfigurationConflictException;

import java.time.Duration;
import java.util.Objects;

record WaitConfig(long everyNanos, long upToNanos, long stableForNanos) {

    static final long DEFAULT_EVERY_NANOS = Duration.ofMillis(100).toNanos();
    static final long DEFAULT_UP_TO_NANOS = Duration.ofSeconds(10).toNanos();

    static WaitConfig defaults() {
        return new WaitConfig(DEFAULT_EVERY_NANOS, DEFAULT_UP_TO_NANOS, 0L);
    }

    WaitConfig withEvery(Duration value) {
        return new WaitConfig(positiveNanos(value, "poll interval"),
                upToNanos, stableForNanos);
    }

    WaitConfig withUpTo(Duration value) {
        WaitConfig candidate = new WaitConfig(everyNanos,
                positiveNanos(value, "acquisition timeout"), stableForNanos);
        candidate.validatePair();
        return candidate;
    }

    WaitConfig withStableFor(Duration value) {
        return new WaitConfig(everyNanos, upToNanos,
                nonNegativeNanos(value, "stability duration"));
    }

    void validatePair() {
        if (everyNanos >= upToNanos) {
            throw new AwaitConfigurationConflictException(
                    "poll interval (" + DurationFormatter.format(everyNanos)
                            + ") must be shorter than acquisition timeout ("
                            + DurationFormatter.format(upToNanos) + ")");
        }
    }

    private static long positiveNanos(Duration value, String label) {
        long nanos = nanos(value);
        if (nanos <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return nanos;
    }

    private static long nonNegativeNanos(Duration value, String label) {
        long nanos = nanos(value);
        if (nanos < 0) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return nanos;
    }

    private static long nanos(Duration value) {
        Objects.requireNonNull(value, "duration must not be null");
        try {
            return value.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "duration exceeds the supported nanosecond range", overflow);
        }
    }
}
