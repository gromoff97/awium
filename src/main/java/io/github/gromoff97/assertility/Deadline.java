package io.github.gromoff97.assertility;

final class Deadline {

    private Deadline() {
    }

    static long after(long now, long durationNanos) {
        return now + durationNanos;
    }

    static boolean reached(long now, long deadline) {
        return now - deadline >= 0;
    }

    static long remaining(long now, long deadline) {
        long value = deadline - now;
        return value <= 0 ? 0 : value;
    }
}
