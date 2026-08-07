package io.github.gromoff97.assertility.api;

import java.util.concurrent.Future;

public interface FutureAwait<F extends Future<?>> extends FutureTerminals<F, F> {
    FutureTerminals<F, F> as(String description);

    FutureTerminals<F, F> as(String format, Object... args);
}
