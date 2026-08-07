package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

import java.util.List;
import java.util.SequencedCollection;

public interface TrySequencedCollectionAwait<E, C extends SequencedCollection<E>>
        extends SequencedCollectionTerminals<
                E, C, AwaitResult<C>, AwaitResult<E>, AwaitResult<List<E>>> {
}
