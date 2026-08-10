package io.github.gromoff97.awium;

import io.github.gromoff97.awium.exception.*;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        WaitConfig config = WaitConfig.defaults();

        assertEquals(Duration.ofMillis(100).toNanos(), config.everyNanos());
        assertEquals(Duration.ofSeconds(10).toNanos(), config.upToNanos());
        assertEquals(0L, config.stableForNanos());
        config.validatePair();
    }

    @Test
    void acceptsTheSmallestStrictlyValidDurationPair() {
        WaitConfig config = WaitConfig.defaults()
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
        WaitConfig defaults = WaitConfig.defaults();
        WaitConfig interval = defaults.withEvery(Duration.ofSeconds(20));
        WaitConfig stable = defaults.withStableFor(Duration.ofSeconds(2));

        assertNotSame(defaults, interval);
        assertNotSame(defaults, stable);
        assertEquals(WaitConfig.defaults(), defaults);
        assertEquals(Duration.ofSeconds(20).toNanos(), interval.everyNanos());
        assertEquals(Duration.ofSeconds(2).toNanos(), stable.stableForNanos());

        assertThrows(AwaitConfigurationConflictException.class,
                () -> interval.withUpTo(Duration.ofSeconds(10)));
        WaitConfig validBranch = interval.withUpTo(Duration.ofSeconds(30));
        assertEquals(Duration.ofSeconds(30).toNanos(), validBranch.upToNanos());
        assertEquals(Duration.ofSeconds(10).toNanos(), interval.upToNanos());
    }

    @Test
    void eachDurationRejectsNullBeforeOtherValidation() {
        WaitConfig invalidPair = WaitConfig.defaults().withEvery(Duration.ofSeconds(20));

        for (Function<Duration, WaitConfig> operation
                : List.<Function<Duration, WaitConfig>>of(
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
                    () -> WaitConfig.defaults().withEvery(invalid));
            assertEquals("poll interval must be greater than zero",
                    intervalFailure.getMessage());

            var timeoutFailure = assertThrows(IllegalArgumentException.class,
                    () -> WaitConfig.defaults()
                            .withEvery(Duration.ofSeconds(20))
                            .withUpTo(invalid));
            assertEquals("acquisition timeout must be greater than zero",
                    timeoutFailure.getMessage());
        }
    }

    @Test
    void stabilityMayBeZeroButNotNegative() {
        assertEquals(0L, WaitConfig.defaults()
                .withStableFor(Duration.ZERO)
                .stableForNanos());

        var failure = assertThrows(IllegalArgumentException.class,
                () -> WaitConfig.defaults().withStableFor(Duration.ofNanos(-1)));
        assertEquals("stability duration must not be negative", failure.getMessage());
    }

    @Test
    void durationsMustFitInSignedLongNanoseconds() {
        Duration maximum = Duration.ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
                WaitConfig.defaults().withEvery(maximum).everyNanos());
        assertEquals(Long.MAX_VALUE,
                WaitConfig.defaults().withUpTo(maximum).upToNanos());
        assertEquals(Long.MAX_VALUE,
                WaitConfig.defaults().withStableFor(maximum).stableForNanos());

        Duration overflow = Duration.ofSeconds(Long.MAX_VALUE);
        for (Function<Duration, WaitConfig> operation
                : List.<Function<Duration, WaitConfig>>of(
                WaitConfig.defaults()::withEvery,
                WaitConfig.defaults()::withUpTo,
                WaitConfig.defaults()::withStableFor)) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> operation.apply(overflow));
            assertEquals("duration exceeds the supported nanosecond range",
                    failure.getMessage());
            assertInstanceOf(ArithmeticException.class, failure.getCause());
        }
    }

    @Test
    void onlyTimeoutAndTerminalValidationCheckThePair() {
        WaitConfig interval = WaitConfig.defaults().withEvery(Duration.ofSeconds(20));
        WaitConfig stable = interval.withStableFor(Duration.ofSeconds(1));

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
                () -> WaitConfig.defaults().withUpTo(Duration.ofMillis(100)));

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
        assertEquals("1 nanosecond", DurationFormatter.format(1));
        assertEquals("100 milliseconds", DurationFormatter.format(100_000_000));
        assertEquals("1 minute 30 seconds",
                DurationFormatter.format(Duration.ofSeconds(90).toNanos()));
        assertEquals("1 second 1 millisecond 1 microsecond 1 nanosecond",
                DurationFormatter.format(1_001_001_001));
    }

    @Test
    void deadlinesRemainCorrectAcrossNanoTimeWraparound() {
        long now = Long.MAX_VALUE - 2;
        long deadline = Deadline.after(now, 5);

        assertEquals(Long.MIN_VALUE + 2, deadline);
        assertFalse(Deadline.reached(now, deadline));
        assertEquals(5, Deadline.remaining(now, deadline));
        assertFalse(Deadline.reached(now + 4, deadline));
        assertEquals(1, Deadline.remaining(now + 4, deadline));
        assertTrue(Deadline.reached(deadline, deadline));
        assertEquals(0, Deadline.remaining(deadline, deadline));
        assertTrue(Deadline.reached(deadline + 1, deadline));
        assertEquals(0, Deadline.remaining(deadline + 1, deadline));
    }

    @Test
    void fakeTimeImplementsBothTimePortsWithoutSleeping() {
        FakeTime time = new FakeTime(Long.MAX_VALUE - 2);
        NanoClock clock = time;
        Parker parker = time;

        parker.parkNanos(5);

        assertEquals(Long.MIN_VALUE + 2, clock.nanoTime());
    }

    @Test
    void jdkTimeExposesSingletonDelegates() {
        long before = JdkTime.CLOCK.nanoTime();
        JdkTime.PARKER.parkNanos(0);
        long after = JdkTime.CLOCK.nanoTime();

        assertTrue(after - before >= 0);
    }
}
