package io.github.gromoff97.assertility;

final class FakeTime implements NanoClock, Parker {

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
        nowNanos += nanos;
    }
}
