package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.SequencedCollection;

public sealed interface SequencedCollectionAwait<
        E, C extends SequencedCollection<E>>
        extends CollectionAwait.Until<E, C>
        permits SequencedCollectionStages.SequencedCollectionInitialStage {

    AfterEvery<E, C> every(Duration interval);

    AfterUpTo<E, C> upTo(Duration timeout);

    Until<E, C> stableFor(Duration stability);

    sealed interface Until<E, C extends SequencedCollection<E>>
            extends CollectionAwait.Until<E, C>
            permits AfterUpTo,
                    SequencedCollectionStages.SequencedCollectionTerminalStage {
    }

    sealed interface AfterEvery<E, C extends SequencedCollection<E>>
            extends AfterUpTo<E, C>
            permits SequencedCollectionStages
                    .SequencedCollectionAfterEveryStage {

        AfterUpTo<E, C> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<E, C extends SequencedCollection<E>>
            extends Until<E, C>
            permits AfterEvery, SequencedCollectionStages
                    .SequencedCollectionAfterUpToStage {

        Until<E, C> stableFor(Duration stability);
    }
}
