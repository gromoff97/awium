package io.github.gromoff97.awium.sources;

import java.util.Collection;

@FunctionalInterface
public interface CollectionSource<C extends Collection<?>> extends Source<C> {
}
