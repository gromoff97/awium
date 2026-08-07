package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitSources;

public interface TryAwaitFactory {
    TryBooleanAwait until(AwaitSources.BooleanSource source);

    <T extends Comparable<? super T>> TryComparableAwait<T> until(
            AwaitSources.ComparableSource<T> source);

    TryStringAwait until(AwaitSources.StringSource source);

    <T> TryOptionalAwait<T> until(AwaitSources.OptionalSource<T> source);

    <E, C extends java.util.SequencedCollection<E>> TrySequencedCollectionAwait<E, C> until(
            AwaitSources.SequencedCollectionSource<E, C> source);

    <E, C extends java.util.Collection<E>> TryCollectionAwait<E, C> until(
            AwaitSources.CollectionSource<E, C> source);

    <K, V, M extends java.util.Map<K, V>> TryMapAwait<K, V, M> until(
            AwaitSources.MapSource<K, V, M> source);

    <F extends java.util.concurrent.Future<?>> TryFutureAwait<F> until(
            AwaitSources.FutureSource<F> source);

    TryExecutableAwait until(AwaitSources.Executable source);

    <T> TryObjectAwait<T> until(AwaitSources.Source<T> source);
}
