package io.github.gromoff97.awium.sources;

import java.util.Map;

@FunctionalInterface
public interface MapSource<M extends Map<?, ?>> extends Source<M> {
}
