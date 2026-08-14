package io.github.gromoff97.awium.sources;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@FunctionalInterface
public interface Source<T> {

    T get() throws Exception;

    @FunctionalInterface
    interface OptionalSource<T> extends Source<Optional<T>> {
    }

    @FunctionalInterface
    interface CollectionSource<C extends Collection<?>> extends Source<C> {
    }

    @FunctionalInterface
    interface MapSource<M extends Map<?, ?>> extends Source<M> {
    }
}
