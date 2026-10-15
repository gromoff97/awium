package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Checked supplier observed on every poll; nested markers recover selected element types for structured sources.
 *
 * @param <OBSERVED> complete value returned by each observation
 */
@FunctionalInterface
public interface Source<OBSERVED> {

    OBSERVED get() throws Exception;

    @FunctionalInterface
    interface OptionalSource<VALUE> extends Source<Optional<VALUE>> {
    }

    @FunctionalInterface
    interface CollectionSource<VALUES extends Collection<?>> extends Source<VALUES> {
    }

    record CollectionViewSource<ELEMENT, VALUES extends Collection<? extends ELEMENT>>(
            Source<? extends VALUES> delegate) implements CollectionSource<VALUES> {

        public CollectionViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public VALUES get() throws Exception {
            return delegate.get();
        }
    }

    @FunctionalInterface
    interface MapSource<ENTRIES extends Map<?, ?>> extends Source<ENTRIES> {
    }

    record MapViewSource<KEY, VALUE, ENTRIES extends Map<? extends KEY, ? extends VALUE>>(
            Source<? extends ENTRIES> delegate) implements MapSource<ENTRIES> {

        public MapViewSource {
            requireNonNull(delegate, "delegate must not be null");
        }

        @Override
        public ENTRIES get() throws Exception {
            return delegate.get();
        }
    }
}
