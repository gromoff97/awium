package io.github.gromoff97.assertility.api;

import java.util.List;
import java.util.SequencedCollection;

public interface SequencedCollectionAwait<E, C extends SequencedCollection<E>>
        extends SequencedCollectionTerminals<E, C, C, E, List<E>> {
    SequencedCollectionTerminals<E, C, C, E, List<E>> as(String description);

    SequencedCollectionTerminals<E, C, C, E, List<E>> as(String format, Object... args);
}
