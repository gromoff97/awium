package io.github.gromoff97.awium.internal;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.gromoff97.awium.internal.WaitConfiguration.DEFAULT;
import static java.time.Duration.*;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.github.gromoff97.awium.internal.DiagnosticText.unsignedDuration;

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
        assertThatNullPointerException().isThrownBy(() -> DEFAULT.withEvery(null))
                .withMessage("polling interval must not be null");
        assertThatNullPointerException().isThrownBy(() -> DEFAULT.withUpTo(null))
                .withMessage("acquisition timeout must not be null");
        assertThatNullPointerException().isThrownBy(() -> DEFAULT.withPersistence(null))
                .withMessage("persistence duration must not be null");
    }

    @Test
    void intervalAndTimeoutMustBePositive() {
        for (Duration invalid : List.of(ZERO, ofNanos(-1))) {
            assertThatIllegalArgumentException().isThrownBy(() -> DEFAULT.withEvery(invalid))
                    .withMessageContaining("polling interval");
            assertThatIllegalArgumentException().isThrownBy(() -> DEFAULT.withEvery(ofSeconds(20)).withUpTo(invalid))
                    .withMessageContaining("acquisition timeout");
        }
    }

    @Test
    void persistenceMayBeZeroButNotNegative() {
        assertEquals(0L, DEFAULT.withPersistence(ZERO).persistenceNanos());

        assertThatIllegalArgumentException().isThrownBy(() -> DEFAULT.withPersistence(ofNanos(-1)))
                .withMessageContaining("persistence");
    }

    @Test
    void rawConfigurationCannotBypassDurationValidation() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WaitConfiguration(0, 1, 0, DEFAULT.clock(), DEFAULT.parker()))
                .withMessage("polling interval must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WaitConfiguration(1, 0, 0, DEFAULT.clock(), DEFAULT.parker()))
                .withMessage("acquisition timeout must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WaitConfiguration(1, 2, -1, DEFAULT.clock(), DEFAULT.parker()))
                .withMessage("persistence duration must be non-negative");
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
        assertEquals("106751 d 23 h 47 min 16 s 854 ms 775 µs 808 ns",
                unsignedDuration(Long.MIN_VALUE));
        assertEquals("0 ns", unsignedDuration(0));
        assertEquals("213503 d 23 h 34 min 33 s 709 ms 551 µs 615 ns", unsignedDuration(-1));
        assertEquals("1 ns", unsignedDuration(1));
        assertEquals("1 min 30 s", unsignedDuration(90_000_000_000L));
        assertEquals("1 s 1 ms 1 µs 1 ns", unsignedDuration(1_001_001_001));
    }
}
