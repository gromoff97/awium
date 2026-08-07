package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitSources;

public interface AwaitFactory {
    BooleanAwait until(AwaitSources.BooleanSource source);

    <T extends Comparable<? super T>> ComparableAwait<T> until(
            AwaitSources.ComparableSource<T> source);

    StringAwait until(AwaitSources.StringSource source);

    <T> OptionalAwait<T> until(AwaitSources.OptionalSource<T> source);

    <E, C extends java.util.Collection<E>> CollectionAwait<E, C> until(
            AwaitSources.CollectionSource<E, C> source);

    <T> ObjectAwait<T> until(AwaitSources.Source<T> source);
}
