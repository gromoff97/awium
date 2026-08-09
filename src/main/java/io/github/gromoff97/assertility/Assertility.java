package io.github.gromoff97.assertility;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;

public final class Assertility {

    private Assertility() {
    }

    public static <T> ObjectAwait<T> await(AwaitSources.Source<T> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new ObjectStageAdapters.ObjectInitialStage<>(
                new AwaitChain<>(source));
    }

    public static <T> OptionalAwait<T> await(
            AwaitSources.OptionalSource<T> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new OptionalStageAdapters.OptionalInitialStage<>(
                new AwaitChain<>(source));
    }

    public static <E, C extends Collection<E>> CollectionAwait<E, C> await(
            AwaitSources.CollectionSource<E, C> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new CollectionStageAdapters.CollectionInitialStage<>(
                new AwaitChain<>(source));
    }

    public static <E, C extends SequencedCollection<E>>
            SequencedCollectionAwait<E, C> await(
                    AwaitSources.SequencedCollectionSource<E, C> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new SequencedCollectionStageAdapters
                .SequencedCollectionInitialStage<>(new AwaitChain<>(source));
    }

    public static <K, V, M extends Map<K, V>> MapAwait<K, V, M> await(
            AwaitSources.MapSource<K, V, M> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new MapStageAdapters.MapInitialStage<>(new AwaitChain<>(source));
    }
}
