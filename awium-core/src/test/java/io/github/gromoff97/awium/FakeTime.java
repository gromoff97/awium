package io.github.gromoff97.awium;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.lang.Math.min;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FakeTime implements LongSupplier, LongConsumer {

    private static final int MAX_TIME_OPERATIONS = 100_000;

    private final ArrayDeque<Long> parkAdvances = new ArrayDeque<>();
    final List<Long> parkRequests = new ArrayList<>();
    private long nowNanos;
    private int operations;

    FakeTime(long nowNanos) {
        this.nowNanos = nowNanos;
    }

    @Override
    public long getAsLong() {
        assertTrue(++operations <= MAX_TIME_OPERATIONS,
                "test exceeded " + MAX_TIME_OPERATIONS + " virtual time operations");
        return nowNanos;
    }

    @Override
    public void accept(long nanos) {
        parkRequests.add(nanos);
        advanceNanos(parkAdvances.isEmpty()
                ? nanos
                : min(nanos, parkAdvances.removeFirst()));
    }

    void advanceNanos(long nanos) {
        nowNanos = getAsLong() + nanos;
    }

    void wakeAfter(long nanos) {
        parkAdvances.addLast(nanos);
    }
}
