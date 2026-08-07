package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

import java.util.Optional;

public interface TryOptionalAwait<T>
        extends OptionalTerminals<T, AwaitResult<Optional<T>>, AwaitResult<T>> {
}
