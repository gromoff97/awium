package io.github.gromoff97.awium.sources;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

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

    @FunctionalInterface
    interface MapSource<Entries extends Map<?, ?>> extends Source<Entries> {
    }
}
