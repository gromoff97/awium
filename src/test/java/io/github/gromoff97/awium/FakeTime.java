package io.github.gromoff97.awium;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

final class FakeTime implements NanoClock, Parker {

    private final ArrayDeque<Long> parkAdvances = new ArrayDeque<>();
    private final List<Long> parkRequests = new ArrayList<>();
    private long nowNanos;

    FakeTime(long nowNanos) {
        this.nowNanos = nowNanos;
    }

    @Override
    public long nanoTime() {
        return nowNanos;
    }

    @Override
    public void parkNanos(long nanos) {
        parkRequests.add(nanos);
        nowNanos += parkAdvances.isEmpty()
                ? nanos
                : Math.min(nanos, parkAdvances.removeFirst());
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
