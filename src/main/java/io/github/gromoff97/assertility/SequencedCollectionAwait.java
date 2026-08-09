package io.github.gromoff97.assertility;

import java.time.Duration;
import java.util.SequencedCollection;

public sealed interface SequencedCollectionAwait<
        E, C extends SequencedCollection<E>>
        extends SequencedCollectionUntil<E, C>
        permits SequencedCollectionStageAdapters.SequencedCollectionInitialStage {

    AfterEvery<E, C> every(Duration interval);

    AfterUpTo<E, C> upTo(Duration timeout);

    SequencedCollectionUntil<E, C> stableFor(Duration stability);

    sealed interface AfterEvery<E, C extends SequencedCollection<E>>
            extends AfterUpTo<E, C>
            permits SequencedCollectionStageAdapters
                    .SequencedCollectionAfterEveryStage {

        AfterUpTo<E, C> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<E, C extends SequencedCollection<E>>
            extends SequencedCollectionUntil<E, C>
            permits AfterEvery, SequencedCollectionStageAdapters
                    .SequencedCollectionAfterUpToStage {

        SequencedCollectionUntil<E, C> stableFor(Duration stability);
    }
}
