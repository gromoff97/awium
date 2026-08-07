package io.github.gromoff97.assertility.api;

import java.util.concurrent.Future;

public interface FutureTerminals<F extends Future<?>, R> extends ObjectTerminals<F, R> {
    R isDone();
}
