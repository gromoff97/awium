package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

public interface TryComparableAwait<T extends Comparable<? super T>>
        extends ComparableTerminals<T, AwaitResult<T>> {
}
