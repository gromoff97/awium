package io.github.gromoff97.assertility.api;

import java.util.Collection;
import java.util.List;

public interface CollectionAwait<E, C extends Collection<E>>
        extends CollectionTerminals<E, C, C, E, List<E>> {
    CollectionTerminals<E, C, C, E, List<E>> as(String description);

    CollectionTerminals<E, C, C, E, List<E>> as(String format, Object... args);
}
