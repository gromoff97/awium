package io.github.gromoff97.assertility;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.concurrent.Future;

public final class AwaitSources {
    private AwaitSources() {
    }

    @FunctionalInterface
    public interface Source<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface Executable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface BooleanSource extends Source<Boolean> {
    }

    @FunctionalInterface
    public interface ComparableSource<T extends Comparable<? super T>> extends Source<T> {
    }

    @FunctionalInterface
    public interface StringSource extends ComparableSource<String> {
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

    @FunctionalInterface
    public interface FutureSource<F extends Future<?>> extends Source<F> {
    }
}
