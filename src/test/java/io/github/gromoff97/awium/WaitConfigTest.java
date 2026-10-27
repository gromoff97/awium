package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.time.Duration.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitConfigTest {

    @Test
    void defaultsUseTheSpecifiedEffectiveDurations() {
        WaitConfiguration config = DEFAULT;

        assertEquals(ofMillis(100).toNanos(), config.everyNanos());
        assertEquals(ofSeconds(10).toNanos(), config.upToNanos());
        assertEquals(0L, config.persistenceNanos());
    }

    @Test
    void acceptsTheSmallestPositiveDurations() {
        WaitConfiguration config = DEFAULT.withEvery(ofNanos(1)).withUpTo(ofNanos(1));

        assertEquals(1, config.everyNanos());
        assertEquals(1, config.upToNanos());
    }

    @Test
    void eachDurationRejectsNullBeforeOtherValidation() {
        assertEquals("polling interval must not be null",
                assertThrows(NullPointerException.class,
                        () -> DEFAULT.withEvery(null)).getMessage());
        assertEquals("acquisition timeout must not be null",
                assertThrows(NullPointerException.class,
                        () -> DEFAULT.withUpTo(null)).getMessage());
        assertEquals("persistence duration must not be null",
                assertThrows(NullPointerException.class,
                        () -> DEFAULT.withPersistence(null)).getMessage());
    }

    @Test
    void intervalAndTimeoutMustBePositive() {
        for (Duration invalid : List.of(ZERO, ofNanos(-1))) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> DEFAULT.withEvery(invalid)).getMessage()
                    .contains("polling interval"));
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> DEFAULT.withEvery(ofSeconds(20)).withUpTo(invalid))
                    .getMessage().contains("acquisition timeout"));
        }
    }

    @Test
    void persistenceMayBeZeroButNotNegative() {
        assertEquals(0L, DEFAULT.withPersistence(ZERO).persistenceNanos());

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> DEFAULT.withPersistence(ofNanos(-1))).getMessage()
                .contains("persistence"));
    }

    @Test
    void rawConfigurationCannotBypassDurationValidation() {
        assertEquals("polling interval must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> new WaitConfiguration(0, 1, 0, DEFAULT.clock(), DEFAULT.parker())).getMessage());
        assertEquals("acquisition timeout must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> new WaitConfiguration(1, 0, 0, DEFAULT.clock(), DEFAULT.parker())).getMessage());
        assertEquals("persistence duration must be non-negative",
                assertThrows(IllegalArgumentException.class,
                        () -> new WaitConfiguration(1, 2, -1, DEFAULT.clock(), DEFAULT.parker())).getMessage());
    }

    @Test
    void durationsMustFitInSignedLongNanoseconds() {
        Duration maximum = ofNanos(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE,
                DEFAULT.withEvery(maximum).everyNanos());

        for (Duration overflow : List.of(maximum.plusNanos(1), ofSeconds(Long.MAX_VALUE),
                ofNanos(Long.MIN_VALUE).minusNanos(1))) {
            assertThrows(ArithmeticException.class, () -> DEFAULT.withEvery(overflow));
            assertThrows(ArithmeticException.class, () -> DEFAULT.withUpTo(overflow));
            assertThrows(ArithmeticException.class, () -> DEFAULT.withPersistence(overflow));
        }
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.withEvery(ofNanos(Long.MIN_VALUE)));
    }

    @Test
    void durationFormattingUsesReadableExactUnits() {
        assertEquals("106751 days 23 hours 47 minutes 16 seconds 854 milliseconds 775 microseconds 808 nanoseconds",
                FailureDiagnostics.duration(ofNanos(Long.MAX_VALUE).plusNanos(1)));
        assertEquals("1 nanosecond", FailureDiagnostics.duration(ofNanos(1)));
        assertEquals("1 minute 30 seconds", FailureDiagnostics.duration(ofSeconds(90)));
        assertEquals("1 second 1 millisecond 1 microsecond 1 nanosecond", FailureDiagnostics.duration(ofNanos(1_001_001_001)));
    }
}
