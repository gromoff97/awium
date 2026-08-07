package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitSources;

public interface AwaitFactory {
    BooleanAwait until(AwaitSources.BooleanSource source);

    <T extends Comparable<? super T>> ComparableAwait<T> until(
            AwaitSources.ComparableSource<T> source);

    StringAwait until(AwaitSources.StringSource source);

    <T> OptionalAwait<T> until(AwaitSources.OptionalSource<T> source);

    <E, C extends java.util.SequencedCollection<E>> SequencedCollectionAwait<E, C> until(
            AwaitSources.SequencedCollectionSource<E, C> source);

    <E, C extends java.util.Collection<E>> CollectionAwait<E, C> until(
            AwaitSources.CollectionSource<E, C> source);

    <K, V, M extends java.util.Map<K, V>> MapAwait<K, V, M> until(
            AwaitSources.MapSource<K, V, M> source);

    <F extends java.util.concurrent.Future<?>> FutureAwait<F> until(
            AwaitSources.FutureSource<F> source);

    ExecutableAwait until(AwaitSources.Executable source);

    <T> ObjectAwait<T> until(AwaitSources.Source<T> source);
}
