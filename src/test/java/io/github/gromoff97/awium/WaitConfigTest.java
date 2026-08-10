package io.github.gromoff97.awium;

import io.github.gromoff97.awium.engine.*;

import io.github.gromoff97.awium.exceptions.*;

import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class WaitConfigTest {

    @Test
    void defaultsUseTheSpecifiedEffectiveDurations() {
        WaitConfiguration config = defaults();

        assertEquals(Duration.ofMillis(100).toNanos(), config.everyNanos());
        assertEquals(Duration.ofSeconds(10).toNanos(), config.upToNanos());
        assertEquals(0L, config.stableForNanos());
        config.validatePair();
    }

    @Test
    void acceptsTheSmallestStrictlyValidDurationPair() {
        WaitConfiguration config = defaults()
                .withEvery(Duration.ofNanos(1))
                .withUpTo(Duration.ofNanos(2))
                .withStableFor(Duration.ofNanos(1));

        assertEquals(1, config.everyNanos());
        assertEquals(2, config.upToNanos());
        assertEquals(1, config.stableForNanos());
        config.validatePair();
    }

    @Test
    void configurationCreatesIndependentCandidates() {
        WaitConfiguration defaults = defaults();
        WaitConfiguration interval = defaults.withEvery(Duration.ofSeconds(20));
        WaitConfiguration stable = defaults.withStableFor(Duration.ofSeconds(2));

        assertNotSame(defaults, interval);
        assertNotSame(defaults, stable);
        assertEquals(defaults(), defaults);
        assertEquals(Duration.ofSeconds(20).toNanos(), interval.everyNanos());
        assertEquals(Duration.ofSeconds(2).toNanos(), stable.stableForNanos());

        WaitConfiguration invalidBranch = interval.withUpTo(
                Duration.ofSeconds(10));
        assertThrows(AwaitConfigurationConflictException.class,
                invalidBranch::validatePair);
        WaitConfiguration validBranch = interval.withUpTo(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30).toNanos(), validBranch.upToNanos());
        assertEquals(Duration.ofSeconds(10).toNanos(), interval.upToNanos());
    }

    @Test
    void eachDurationRejectsNullBeforeOtherValidation() {
        WaitConfiguration invalidPair = defaults().withEvery(Duration.ofSeconds(20));

        for (Function<Duration, WaitConfiguration> operation
                : List.<Function<Duration, WaitConfiguration>>of(
                invalidPair::withEvery,
                invalidPair::withUpTo,
                invalidPair::withStableFor)) {
            var failure = assertThrows(NullPointerException.class,
                    () -> operation.apply(null));
            assertEquals("duration must not be null", failure.getMessage());
        }
    }

    @Test
    void intervalAndTimeoutMustBePositive() {
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofNanos(-1))) {
            var intervalFailure = assertThrows(IllegalArgumentException.class,
                    () -> defaults().withEvery(invalid));
            assertEquals("poll interval must be greater than zero",
                    intervalFailure.getMessage());

            var timeoutFailure = assertThrows(IllegalArgumentException.class,
                    () -> defaults()
                            .withEvery(Duration.ofSeconds(20))
                            .withUpTo(invalid));
            assertEquals("acquisition timeout must be greater than zero",
                    timeoutFailure.getMessage());
        }
    }

    @Test
    void stabilityMayBeZeroButNotNegative() {
        assertEquals(0L, defaults()
                .withStableFor(Duration.ZERO)
                .stableForNanos());

        var failure = assertThrows(IllegalArgumentException.class,
                () -> defaults().withStableFor(Duration.ofNanos(-1)));
        assertEquals("stability duration must not be negative", failure.getMessage());
    }

    @Test
    void durationsMustFitInSignedLongNanoseconds() {
        Duration maximum = Duration.ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
                defaults().withEvery(maximum).everyNanos());
        assertEquals(Long.MAX_VALUE,
                defaults().withUpTo(maximum).upToNanos());
        assertEquals(Long.MAX_VALUE,
                defaults().withStableFor(maximum).stableForNanos());

        Duration overflow = Duration.ofSeconds(Long.MAX_VALUE);
        for (Function<Duration, WaitConfiguration> operation
                : List.<Function<Duration, WaitConfiguration>>of(
                defaults()::withEvery,
                defaults()::withUpTo,
                defaults()::withStableFor)) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> operation.apply(overflow));
            assertEquals("duration exceeds the supported nanosecond range",
                    failure.getMessage());
            assertInstanceOf(ArithmeticException.class, failure.getCause());
        }
    }

    @Test
    void onlyTerminalValidationChecksTheFinalPair() {
        WaitConfiguration intermediate = defaults()
                .withEvery(Duration.ofSeconds(20))
                .withUpTo(Duration.ofSeconds(10));
        WaitConfiguration repaired = intermediate
                .withEvery(Duration.ofSeconds(1));

        assertThrows(AwaitConfigurationConflictException.class,
                intermediate::validatePair);
        repaired.validatePair();
    }

    @Test
    void equalIntervalAndTimeoutConflictOnlyWhenValidated() {
        WaitConfiguration equal = defaults()
                .withUpTo(Duration.ofMillis(100));

        var failure = assertThrows(AwaitConfigurationConflictException.class,
                equal::validatePair);
        assertEquals(
                "poll interval (100 milliseconds) must be shorter than acquisition timeout (100 milliseconds)",
                failure.getMessage());
    }

    @Test
    void conflictExceptionIsPublicFinalAndExternallyConstructible()
            throws ReflectiveOperationException {
        assertTrue(isPublic(AwaitConfigurationConflictException.class.getModifiers()));
        assertTrue(isFinal(AwaitConfigurationConflictException.class.getModifiers()));
        assertTrue(IllegalArgumentException.class
                .isAssignableFrom(AwaitConfigurationConflictException.class));
        assertEquals(1, AwaitConfigurationConflictException.class
                .getDeclaredConstructors().length);
        assertTrue(isPublic(AwaitConfigurationConflictException.class
                .getDeclaredConstructor(String.class).getModifiers()));
    }

    @Test
    void durationFormattingUsesReadableExactUnits() {
        assertEquals(
                "poll interval (1 nanosecond) must be shorter than acquisition timeout (1 nanosecond)",
                conflictMessage(1));
        assertEquals(
                "poll interval (100 milliseconds) must be shorter than acquisition timeout (100 milliseconds)",
                conflictMessage(100_000_000));
        assertEquals(
                "poll interval (1 minute 30 seconds) must be shorter than acquisition timeout (1 minute 30 seconds)",
                conflictMessage(Duration.ofSeconds(90).toNanos()));
        assertEquals(
                "poll interval (1 second 1 millisecond 1 microsecond 1 nanosecond) must be shorter than acquisition timeout (1 second 1 millisecond 1 microsecond 1 nanosecond)",
                conflictMessage(1_001_001_001));
    }

    private static String conflictMessage(long nanos) {
        return assertThrows(AwaitConfigurationConflictException.class,
                () -> new WaitConfiguration(nanos, nanos, 0).validatePair())
                .getMessage();
    }

}
