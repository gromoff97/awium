package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.Collection;

public sealed interface CollectionAwait<E, C extends Collection<E>>
        extends CollectionUntil<E, C>
        permits CollectionStageAdapters.CollectionInitialStage {

    AfterEvery<E, C> every(Duration interval);

    AfterUpTo<E, C> upTo(Duration timeout);

    CollectionUntil<E, C> stableFor(Duration stability);

    sealed interface AfterEvery<E, C extends Collection<E>>
            extends AfterUpTo<E, C>
            permits CollectionStageAdapters.CollectionAfterEveryStage {

        AfterUpTo<E, C> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<E, C extends Collection<E>>
            extends CollectionUntil<E, C>
            permits AfterEvery,
                    CollectionStageAdapters.CollectionAfterUpToStage {

        CollectionUntil<E, C> stableFor(Duration stability);
    }
}
