package io.github.gromoff97.awium;

final class DurationFormatter {

    private static final long[] UNIT_NANOS = {
        86_400_000_000_000L,
        3_600_000_000_000L,
        60_000_000_000L,
        1_000_000_000L,
        1_000_000L,
        1_000L,
        1L
    };
    private static final String[] UNIT_NAMES = {
        "day", "hour", "minute", "second", "millisecond", "microsecond", "nanosecond"
    };

    private DurationFormatter() {
    }

    static String format(long nanos) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < UNIT_NANOS.length; index++) {
            long count = nanos / UNIT_NANOS[index];
            nanos %= UNIT_NANOS[index];
            if (count == 0) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(count).append(' ').append(UNIT_NAMES[index]);
            if (count != 1) {
                result.append('s');
            }
        }
        return result.isEmpty() ? "0 nanoseconds" : result.toString();
    }
}
