package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

import java.util.concurrent.Future;

public interface TryFutureAwait<F extends Future<?>>
        extends FutureTerminals<F, AwaitResult<F>> {
}
