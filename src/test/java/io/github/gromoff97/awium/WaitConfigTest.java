package io.github.gromoff97.awium;

import io.github.gromoff97.awium.engine.*;

import io.github.gromoff97.awium.exceptions.*;

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
        WaitConfiguration config = WaitConfiguration.defaults();

        assertEquals(Duration.ofMillis(100).toNanos(), config.everyNanos());
        assertEquals(Duration.ofSeconds(10).toNanos(), config.upToNanos());
        assertEquals(0L, config.stableForNanos());
        config.validatePair();
    }

    @Test
    void acceptsTheSmallestStrictlyValidDurationPair() {
        WaitConfiguration config = WaitConfiguration.defaults()
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
        WaitConfiguration defaults = WaitConfiguration.defaults();
        WaitConfiguration interval = defaults.withEvery(Duration.ofSeconds(20));
        WaitConfiguration stable = defaults.withStableFor(Duration.ofSeconds(2));

        assertNotSame(defaults, interval);
        assertNotSame(defaults, stable);
        assertEquals(WaitConfiguration.defaults(), defaults);
        assertEquals(Duration.ofSeconds(20).toNanos(), interval.everyNanos());
        assertEquals(Duration.ofSeconds(2).toNanos(), stable.stableForNanos());

        assertThrows(AwaitConfigurationConflictException.class,
                () -> interval.withUpTo(Duration.ofSeconds(10)));
        WaitConfiguration validBranch = interval.withUpTo(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30).toNanos(), validBranch.upToNanos());
        assertEquals(Duration.ofSeconds(10).toNanos(), interval.upToNanos());
    }

    @Test
    void eachDurationRejectsNullBeforeOtherValidation() {
        WaitConfiguration invalidPair = WaitConfiguration.defaults().withEvery(Duration.ofSeconds(20));

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
                    () -> WaitConfiguration.defaults().withEvery(invalid));
            assertEquals("poll interval must be greater than zero",
                    intervalFailure.getMessage());

            var timeoutFailure = assertThrows(IllegalArgumentException.class,
                    () -> WaitConfiguration.defaults()
                            .withEvery(Duration.ofSeconds(20))
                            .withUpTo(invalid));
            assertEquals("acquisition timeout must be greater than zero",
                    timeoutFailure.getMessage());
        }
    }

    @Test
    void stabilityMayBeZeroButNotNegative() {
        assertEquals(0L, WaitConfiguration.defaults()
                .withStableFor(Duration.ZERO)
                .stableForNanos());

        var failure = assertThrows(IllegalArgumentException.class,
                () -> WaitConfiguration.defaults().withStableFor(Duration.ofNanos(-1)));
        assertEquals("stability duration must not be negative", failure.getMessage());
    }

    @Test
    void durationsMustFitInSignedLongNanoseconds() {
        Duration maximum = Duration.ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
                WaitConfiguration.defaults().withEvery(maximum).everyNanos());
        assertEquals(Long.MAX_VALUE,
                WaitConfiguration.defaults().withUpTo(maximum).upToNanos());
        assertEquals(Long.MAX_VALUE,
                WaitConfiguration.defaults().withStableFor(maximum).stableForNanos());

        Duration overflow = Duration.ofSeconds(Long.MAX_VALUE);
        for (Function<Duration, WaitConfiguration> operation
                : List.<Function<Duration, WaitConfiguration>>of(
                WaitConfiguration.defaults()::withEvery,
                WaitConfiguration.defaults()::withUpTo,
                WaitConfiguration.defaults()::withStableFor)) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> operation.apply(overflow));
            assertEquals("duration exceeds the supported nanosecond range",
                    failure.getMessage());
            assertInstanceOf(ArithmeticException.class, failure.getCause());
        }
    }

    @Test
    void onlyTimeoutAndTerminalValidationCheckThePair() {
        WaitConfiguration interval = WaitConfiguration.defaults().withEvery(Duration.ofSeconds(20));
        WaitConfiguration stable = interval.withStableFor(Duration.ofSeconds(1));

        var terminalFailure = assertThrows(AwaitConfigurationConflictException.class,
                stable::validatePair);
        assertEquals(
                "poll interval (20 seconds) must be shorter than acquisition timeout (10 seconds)",
                terminalFailure.getMessage());

        var timeoutFailure = assertThrows(AwaitConfigurationConflictException.class,
                () -> interval.withUpTo(Duration.ofSeconds(10)));
        assertEquals(terminalFailure.getMessage(), timeoutFailure.getMessage());
    }

    @Test
    void equalIntervalAndTimeoutAlsoConflict() {
        var failure = assertThrows(AwaitConfigurationConflictException.class,
                () -> WaitConfiguration.defaults().withUpTo(Duration.ofMillis(100)));

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
        assertTrue(conflictMessage(1).contains("(1 nanosecond)"));
        assertTrue(conflictMessage(100_000_000)
                .contains("(100 milliseconds)"));
        assertTrue(conflictMessage(Duration.ofSeconds(90).toNanos())
                .contains("(1 minute 30 seconds)"));
        assertTrue(conflictMessage(1_001_001_001).contains(
                "(1 second 1 millisecond 1 microsecond 1 nanosecond)"));
    }

    private static String conflictMessage(long nanos) {
        return assertThrows(AwaitConfigurationConflictException.class,
                () -> new WaitConfiguration(nanos, nanos, 0).validatePair())
                .getMessage();
    }

}
