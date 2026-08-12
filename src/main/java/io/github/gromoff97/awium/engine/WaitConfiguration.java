package io.github.gromoff97.awium.engine;

import static io.github.gromoff97.awium.diagnostics.FailureMessage.configurationConflict;
import io.github.gromoff97.awium.exceptions.AwaitConfigurationConflictException;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

public record WaitConfiguration(
        long everyNanos, long upToNanos, long stableForNanos) {

    private static final long DEFAULT_EVERY_NANOS =
            Duration.ofMillis(100).toNanos();
    private static final long DEFAULT_UP_TO_NANOS =
            Duration.ofSeconds(10).toNanos();

    public static WaitConfiguration defaults() {
        return new WaitConfiguration(DEFAULT_EVERY_NANOS,
                DEFAULT_UP_TO_NANOS, 0L);
    }

    public WaitConfiguration withEvery(Duration value) {
        return new WaitConfiguration(positiveNanos(value, "poll interval"),
                upToNanos, stableForNanos);
    }

    public WaitConfiguration withUpTo(Duration value) {
        return new WaitConfiguration(everyNanos,
                positiveNanos(value, "acquisition timeout"), stableForNanos);
    }

    public WaitConfiguration withStableFor(Duration value) {
        return new WaitConfiguration(everyNanos, upToNanos,
                nonNegativeNanos(value, "stability duration"));
    }

    public void validatePair() {
        if (everyNanos >= upToNanos) {
            throw new AwaitConfigurationConflictException(
                    configurationConflict(
                            everyNanos, upToNanos));
        }
    }

    private static long positiveNanos(Duration value, String label) {
        long nanos = nanos(value);
        if (nanos <= 0) {
            throw new IllegalArgumentException(
                    label + " must be greater than zero");
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
        try {
            return requireNonNull(value, "duration must not be null").toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "duration exceeds the supported nanosecond range", overflow);
        }
    }
}
