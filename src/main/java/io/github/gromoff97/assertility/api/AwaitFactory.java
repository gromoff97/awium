package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitSources;

public interface AwaitFactory {
    <T> ObjectAwait<T> until(AwaitSources.Source<T> source);
}
