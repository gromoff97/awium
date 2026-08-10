package io.github.gromoff97.awium.sources;

import java.util.Optional;

@FunctionalInterface
public interface OptionalSource<T> extends Source<Optional<T>> {
}
