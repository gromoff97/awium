package io.github.gromoff97.awium;

import java.time.Duration;

public sealed interface ObjectAwait<T> extends ObjectUntil<T>
        permits ObjectStageAdapters.ObjectInitialStage {

    AfterEvery<T> every(Duration interval);

    AfterUpTo<T> upTo(Duration timeout);

    ObjectUntil<T> stableFor(Duration stability);

    sealed interface AfterEvery<T> extends AfterUpTo<T>
            permits ObjectStageAdapters.ObjectAfterEveryStage {

        AfterUpTo<T> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<T> extends ObjectUntil<T>
            permits AfterEvery, ObjectStageAdapters.ObjectAfterUpToStage {

        ObjectUntil<T> stableFor(Duration stability);
    }
}
