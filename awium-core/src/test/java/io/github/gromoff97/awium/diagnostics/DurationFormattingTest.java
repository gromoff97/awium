package io.github.gromoff97.awium.diagnostics;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.github.gromoff97.awium.internal.DiagnosticText.unsignedDuration;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationFormattingTest {

    @ParameterizedTest
    @CsvSource({
            "-9223372036854775808, 106751 d 23 h 47 min 16 s 854 ms 775 µs 808 ns",
            "0, 0 ns",
            "-1, 213503 d 23 h 34 min 33 s 709 ms 551 µs 615 ns",
            "1, 1 ns",
            "90000000000, 1 min 30 s",
            "1001001001, 1 s 1 ms 1 µs 1 ns"
    })
    void formatsUnsignedNanosecondsInExactUnits(long nanos, String expected) {
        assertEquals(expected, unsignedDuration(nanos));
    }
}
