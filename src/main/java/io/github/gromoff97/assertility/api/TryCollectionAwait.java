package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

import java.util.Collection;
import java.util.List;

public interface TryCollectionAwait<E, C extends Collection<E>> extends CollectionTerminals<
        E, C, AwaitResult<C>, AwaitResult<E>, AwaitResult<List<E>>> {
}
