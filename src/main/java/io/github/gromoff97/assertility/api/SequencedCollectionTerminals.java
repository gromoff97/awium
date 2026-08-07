package io.github.gromoff97.assertility.api;

import java.util.SequencedCollection;

public interface SequencedCollectionTerminals<
        E, C extends SequencedCollection<E>, RC, RE, RL>
        extends CollectionTerminals<E, C, RC, RE, RL> {
    RC containsExactly(E... expected);

    RC containsExactlyElementsOf(Iterable<? extends E> expected);
}
