package io.github.gromoff97.assertility;

import java.time.Duration;

public sealed interface OptionalAwait<T> extends OptionalUntil<T>
        permits OptionalStageAdapters.OptionalInitialStage {

    AfterEvery<T> every(Duration interval);

    AfterUpTo<T> upTo(Duration timeout);

    OptionalUntil<T> stableFor(Duration stability);

    sealed interface AfterEvery<T> extends AfterUpTo<T>
            permits OptionalStageAdapters.OptionalAfterEveryStage {

        AfterUpTo<T> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<T> extends OptionalUntil<T>
            permits AfterEvery, OptionalStageAdapters.OptionalAfterUpToStage {

        OptionalUntil<T> stableFor(Duration stability);
    }
}
