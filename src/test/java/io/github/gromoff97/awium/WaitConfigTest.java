package io.github.gromoff97.awium;

import io.github.gromoff97.awium.engine.*;

import io.github.gromoff97.awium.exceptions.*;

import static io.github.gromoff97.awium.engine.WaitConfiguration.defaults;
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
        assertEquals(0L, config.stableForNanos());
    }

    @Test
    void acceptsTheSmallestStrictlyValidDurationPair() {
        WaitConfiguration config = defaults()
                .withEvery(ofNanos(1))
                .withUpTo(ofNanos(2));

        assertEquals(1, config.everyNanos());
        assertEquals(2, config.upToNanos());
        config.validatePair();
    }

    @Test
    void eachDurationRejectsNullBeforeOtherValidation() {
        WaitConfiguration invalidPair = defaults().withEvery(ofSeconds(20));

        assertTrue(assertThrows(NullPointerException.class,
                () -> invalidPair.withUpTo(null)).getMessage()
                .contains("duration"));
    }

    @Test
    void intervalAndTimeoutMustBePositive() {
        for (Duration invalid : List.of(ZERO, ofNanos(-1))) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> defaults().withEvery(invalid)).getMessage()
                    .contains("poll interval"));
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> defaults().withEvery(ofSeconds(20)).withUpTo(invalid))
                    .getMessage().contains("acquisition timeout"));
        }
    }

    @Test
    void stabilityMayBeZeroButNotNegative() {
        assertEquals(0L, defaults()
                .withStableFor(ZERO)
                .stableForNanos());

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> defaults().withStableFor(ofNanos(-1))).getMessage()
                .contains("stability"));
    }

    @Test
    void durationsMustFitInSignedLongNanoseconds() {
        Duration maximum = ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
                defaults().withEvery(maximum).everyNanos());

        Duration overflow = ofSeconds(Long.MAX_VALUE);
        var failure = assertThrows(IllegalArgumentException.class,
                () -> defaults().withEvery(overflow));
        assertTrue(failure.getMessage().contains("nanosecond"));
        assertInstanceOf(ArithmeticException.class, failure.getCause());
    }

    @Test
    void equalIntervalAndTimeoutConflictOnlyWhenValidated() {
        WaitConfiguration equal = defaults()
                .withUpTo(ofMillis(100));

        String message = assertThrows(AwaitConfigurationConflictException.class,
                equal::validatePair).getMessage();
        assertTrue(message.contains("poll interval"));
        assertTrue(message.contains("acquisition timeout"));
        assertTrue(message.contains("100 milliseconds"));
    }

    @Test
    void durationFormattingUsesReadableExactUnits() {
        assertEquals(
                "poll interval (0 nanoseconds) must be shorter than acquisition timeout (0 nanoseconds)",
                conflictMessage(0));
        assertEquals(
                "poll interval (1 nanosecond) must be shorter than acquisition timeout (1 nanosecond)",
                conflictMessage(1));
        assertEquals(
                "poll interval (1 minute 30 seconds) must be shorter than acquisition timeout (1 minute 30 seconds)",
                conflictMessage(ofSeconds(90).toNanos()));
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
