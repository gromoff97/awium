package io.github.gromoff97.awium.engine;

import static io.github.gromoff97.awium.diagnostics.FailureMessage.configurationConflict;
import io.github.gromoff97.awium.exceptions.AwaitConfigurationConflictException;

import java.time.Duration;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;

public record WaitConfiguration(
        long everyNanos, long upToNanos, long stableForNanos) {

    public static WaitConfiguration defaults() {
        return new WaitConfiguration(ofMillis(100).toNanos(),
                ofSeconds(10).toNanos(), 0L);
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
        long nanos = nanos(value);
        if (nanos < 0) {
            throw new IllegalArgumentException(
                    "stability duration must not be negative");
        }
        return new WaitConfiguration(everyNanos, upToNanos, nanos);
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

    private static long nanos(Duration value) {
        try {
            return requireNonNull(value, "duration must not be null").toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "duration exceeds the supported nanosecond range", overflow);
        }
    }
}
