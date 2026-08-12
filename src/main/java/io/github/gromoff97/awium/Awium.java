package io.github.gromoff97.awium;

import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.Map;

public final class Awium {

    private Awium() {
        throw new AssertionError("Utility class");
    }

    public static <T> Await<T> await(Source<T> source) {
        return new Await<>(source);
    }

    public static <T> OptionalAwait<T> await(OptionalSource<T> source) {
        return new OptionalAwait<>(source);
    }

    public static <C extends Collection<?>> StructuralAwait<C> await(
            CollectionSource<C> source) {
        return new StructuralAwait<>(source,
                "collection", Collection::size);
    }

    public static <M extends Map<?, ?>> StructuralAwait<M> await(
            MapSource<M> source) {
        return new StructuralAwait<>(source,
                "map", Map::size);
    }
}
