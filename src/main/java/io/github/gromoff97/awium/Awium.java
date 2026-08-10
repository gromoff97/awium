package io.github.gromoff97.awium;

import io.github.gromoff97.awium.await.Await;
import io.github.gromoff97.awium.await.OptionalAwait;
import io.github.gromoff97.awium.await.StructuralAwait;
import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.await.stages.OptionalAwaitStage;
import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public final class Awium {

    private Awium() {
    }

    public static <T> Await<T> await(Source<T> source) {
        return new AwaitStage<>(Objects.requireNonNull(
                source, "source must not be null"));
    }

    public static <T> OptionalAwait<T> await(
            OptionalSource<T> source) {
        return new OptionalAwaitStage<>(Objects.requireNonNull(
                source, "source must not be null"));
    }

    public static <C extends Collection<?>> StructuralAwait<C> await(
            CollectionSource<C> source) {
        return new StructuralAwaitStage<>(Objects.requireNonNull(
                source, "source must not be null"), Collection::size);
    }

    public static <M extends Map<?, ?>> StructuralAwait<M> await(
            MapSource<M> source) {
        return new StructuralAwaitStage<>(Objects.requireNonNull(
                source, "source must not be null"), Map::size);
    }
}
