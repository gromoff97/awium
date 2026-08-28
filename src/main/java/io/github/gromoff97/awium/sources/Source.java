package io.github.gromoff97.awium.sources;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Checked supplier observed on every poll; nested markers recover selected element types for structured sources.
 *
 * @param <Observed> complete value returned by each observation
 */
@FunctionalInterface
public interface Source<Observed> {

    Observed get() throws Exception;

    @FunctionalInterface
    interface OptionalSource<Value> extends Source<Optional<Value>> {
    }

    @FunctionalInterface
    interface CollectionSource<Values extends Collection<?>> extends Source<Values> {
    }

    record CollectionViewSource<Element, Values extends Collection<? extends Element>>(
            Source<? extends Values> delegate) implements CollectionSource<Values> {

        public CollectionViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public Values get() throws Exception {
            return delegate.get();
        }
    }

    @FunctionalInterface
    interface MapSource<Entries extends Map<?, ?>> extends Source<Entries> {
    }

    record MapViewSource<Key, Value, Entries extends Map<? extends Key, ? extends Value>>(
            Source<? extends Entries> delegate) implements MapSource<Entries> {

        public MapViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public Entries get() throws Exception {
            return delegate.get();
        }
    }
}
