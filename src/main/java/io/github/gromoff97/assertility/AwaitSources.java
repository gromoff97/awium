package io.github.gromoff97.assertility;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;

public final class AwaitSources {

    private AwaitSources() {
    }

    @FunctionalInterface
    public interface Source<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface OptionalSource<T> extends Source<Optional<T>> {
    }

    @FunctionalInterface
    public interface CollectionSource<E, C extends Collection<E>> extends Source<C> {
    }

    @FunctionalInterface
    public interface SequencedCollectionSource<E, C extends SequencedCollection<E>>
            extends CollectionSource<E, C> {
    }

    @FunctionalInterface
    public interface MapSource<K, V, M extends Map<K, V>> extends Source<M> {
    }
}
