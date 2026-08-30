package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.engine.WaitConfiguration;

import io.github.gromoff97.awium.exceptions.*;

import static io.github.gromoff97.awium.internal.engine.WaitConfiguration.defaults;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaitConfigTest {

    @Test
    void defaultsUseTheSpecifiedEffectiveDurations() {
        WaitConfiguration config = defaults();

        assertEquals(ofMillis(100).toNanos(), config.everyNanos());
        assertEquals(ofSeconds(10).toNanos(), config.upToNanos());
        assertEquals(0L, config.persistenceNanos());
    }

    @Test
    void acceptsTheSmallestStrictlyValidDurationPair() {
        WaitConfiguration config = defaults().withEvery(ofNanos(1)).withUpTo(ofNanos(2));

        assertEquals(1, config.everyNanos());
        assertEquals(2, config.upToNanos());
        config.validatePair();
    }

    @Test
    void eachDurationRejectsNullBeforeOtherValidation() {
        assertEquals("polling interval must not be null",
                assertThrows(NullPointerException.class,
                        () -> defaults().withEvery(null)).getMessage());
        assertEquals("acquisition timeout must not be null",
                assertThrows(NullPointerException.class,
                        () -> defaults().withUpTo(null)).getMessage());
        assertEquals("persistence duration must not be null",
                assertThrows(NullPointerException.class,
                        () -> defaults().withPersistence(null)).getMessage());
    }

    @Test
    void intervalAndTimeoutMustBePositive() {
        for (Duration invalid : List.of(ZERO, ofNanos(-1))) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> defaults().withEvery(invalid)).getMessage()
                    .contains("polling interval"));
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> defaults().withEvery(ofSeconds(20)).withUpTo(invalid))
                    .getMessage().contains("acquisition timeout"));
        }
    }

    @Test
    void persistenceMayBeZeroButNotNegative() {
        assertEquals(0L, defaults().withPersistence(ZERO).persistenceNanos());

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> defaults().withPersistence(ofNanos(-1))).getMessage()
                .contains("persistence"));
    }

    @Test
    void rawConfigurationCannotBypassDurationValidation() {
        assertEquals("polling interval must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> new WaitConfiguration(0, 1, 0)).getMessage());
        assertEquals("acquisition timeout must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> new WaitConfiguration(1, 0, 0)).getMessage());
        assertEquals("persistence duration must be non-negative",
                assertThrows(IllegalArgumentException.class,
                        () -> new WaitConfiguration(1, 2, -1)).getMessage());
    }

    @Test
    void durationsMustFitInSignedLongNanoseconds() {
        Duration maximum = ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
                defaults().withEvery(maximum).everyNanos());

        Duration overflow = ofSeconds(Long.MAX_VALUE);
        var intervalFailure = assertThrows(IllegalArgumentException.class,
                () -> defaults().withEvery(overflow));
        var timeoutFailure = assertThrows(IllegalArgumentException.class,
                () -> defaults().withUpTo(overflow));
        var persistenceFailure = assertThrows(IllegalArgumentException.class,
                () -> defaults().withPersistence(overflow));
        assertEquals("polling interval exceeds the supported nanosecond range",
                intervalFailure.getMessage());
        assertEquals("acquisition timeout exceeds the supported nanosecond range",
                timeoutFailure.getMessage());
        assertEquals("persistence duration exceeds the supported nanosecond range",
                persistenceFailure.getMessage());
        assertInstanceOf(ArithmeticException.class, intervalFailure.getCause());
        assertInstanceOf(ArithmeticException.class, timeoutFailure.getCause());
        assertInstanceOf(ArithmeticException.class, persistenceFailure.getCause());
    }

    @Test
    void equalIntervalAndTimeoutConflictOnlyWhenValidated() {
        WaitConfiguration equal = defaults()
                .withUpTo(ofMillis(100));

        String message = assertThrows(AwaitConfigurationConflictException.class,
                equal::validatePair).getMessage();
        assertTrue(message.contains("polling interval"));
        assertTrue(message.contains("acquisition timeout"));
        assertTrue(message.contains("100 milliseconds"));
    }

    @Test
    void durationFormattingUsesReadableExactUnits() {
        assertEquals(
                "polling interval (1 nanosecond) must be shorter than acquisition timeout (1 nanosecond)",
                conflictMessage(1));
        assertEquals(
                "polling interval (1 minute 30 seconds) must be shorter than acquisition timeout (1 minute 30 seconds)",
                conflictMessage(ofSeconds(90).toNanos()));
        assertEquals(
                "polling interval (1 second 1 millisecond 1 microsecond 1 nanosecond) must be shorter than "
                        + "acquisition timeout (1 second 1 millisecond 1 microsecond 1 nanosecond)",
                conflictMessage(1_001_001_001));
    }

    private static String conflictMessage(long nanos) {
        return assertThrows(AwaitConfigurationConflictException.class,
                () -> new WaitConfiguration(nanos, nanos, 0).validatePair())
                .getMessage();
    }
}
