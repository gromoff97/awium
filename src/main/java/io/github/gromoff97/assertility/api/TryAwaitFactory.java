package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitSources;

public interface TryAwaitFactory {
    <T> TryObjectAwait<T> until(AwaitSources.Source<T> source);
}
