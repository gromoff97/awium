package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitSources;

public interface TryAwaitFactory {
    TryBooleanAwait until(AwaitSources.BooleanSource source);

    <T extends Comparable<? super T>> TryComparableAwait<T> until(
            AwaitSources.ComparableSource<T> source);

    TryStringAwait until(AwaitSources.StringSource source);

    <T> TryObjectAwait<T> until(AwaitSources.Source<T> source);
}
