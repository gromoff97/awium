package io.github.gromoff97.assertility;

import java.util.Objects;
import java.util.Optional;

public final class AwaitResult<R> {
    private final boolean success;
    private final R value;
    private final AwaitFailure failure;

    private AwaitResult(boolean success, R value, AwaitFailure failure) {
        this.success = success;
        this.value = value;
        this.failure = failure;
    }

    static <R> AwaitResult<R> success(R value) {
        return new AwaitResult<>(true, value, null);
    }

    static <R> AwaitResult<R> failed(AwaitFailure failure) {
        return new AwaitResult<>(false, null, Objects.requireNonNull(failure, "failure"));
    }

    public boolean isSuccess() {
        return success;
    }

    public R get() {
        if (!success) {
            throw failure;
        }
        return value;
    }

    public Optional<AwaitFailure> failure() {
        return Optional.ofNullable(failure);
    }
}
