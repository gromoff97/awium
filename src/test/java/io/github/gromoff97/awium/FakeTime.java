package io.github.gromoff97.awium;

import static java.lang.Math.min;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class FakeTime implements LongSupplier, LongConsumer {

    private final ArrayDeque<Long> parkAdvances = new ArrayDeque<>();
    private final List<Long> parkRequests = new ArrayList<>();
    private long nowNanos;

    FakeTime(long nowNanos) {
        this.nowNanos = nowNanos;
    }

    @Override
    public long getAsLong() {
        return nowNanos;
    }

    @Override
    public void accept(long nanos) {
        parkRequests.add(nanos);
        nowNanos += parkAdvances.isEmpty()
                ? nanos
                : min(nanos, parkAdvances.removeFirst());
    }

    long nanoTime() {
        return getAsLong();
    }

    void parkNanos(long nanos) {
        accept(nanos);
    }

    void advanceNanos(long nanos) {
        nowNanos += nanos;
    }

    void wakeAfter(long nanos) {
        parkAdvances.addLast(nanos);
    }

    List<Long> parkRequests() {
        return List.copyOf(parkRequests);
    }
}
