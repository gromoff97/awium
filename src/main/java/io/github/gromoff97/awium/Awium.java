package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;

public final class Awium {

    private Awium() {
    }

    public static <T> ObjectAwait<T> await(AwaitSources.Source<T> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new ObjectStages.ObjectInitialStage<>(
                new AwaitChain<>(source));
    }

    public static <T> OptionalAwait<T> await(
            AwaitSources.OptionalSource<T> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new OptionalStages.OptionalInitialStage<>(
                new AwaitChain<>(source));
    }

    public static <E, C extends Collection<E>> CollectionAwait<E, C> await(
            AwaitSources.CollectionSource<E, C> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new CollectionStages.CollectionInitialStage<>(
                new AwaitChain<>(source));
    }

    public static <E, C extends SequencedCollection<E>>
            SequencedCollectionAwait<E, C> await(
                    AwaitSources.SequencedCollectionSource<E, C> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new SequencedCollectionStages
                .SequencedCollectionInitialStage<>(new AwaitChain<>(source));
    }

    public static <K, V, M extends Map<K, V>> MapAwait<K, V, M> await(
            AwaitSources.MapSource<K, V, M> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new MapStages.MapInitialStage<>(new AwaitChain<>(source));
    }
}
