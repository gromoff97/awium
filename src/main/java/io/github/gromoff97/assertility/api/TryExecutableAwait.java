package io.github.gromoff97.assertility.api;

import io.github.gromoff97.assertility.AwaitResult;

public interface TryExecutableAwait {
    AwaitResult<Void> doesNotThrowAnyException();
}
