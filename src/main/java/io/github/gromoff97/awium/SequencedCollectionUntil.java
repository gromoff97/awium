package io.github.gromoff97.awium;

import java.util.SequencedCollection;

public sealed interface SequencedCollectionUntil<
        E, C extends SequencedCollection<E>> extends CollectionUntil<E, C>
        permits SequencedCollectionAwait,
                SequencedCollectionAwait.AfterUpTo,
                SequencedCollectionStageAdapters
                        .SequencedCollectionTerminalStage {
}
