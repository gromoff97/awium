package io.github.gromoff97.awium;

import java.util.Collection;

public sealed interface CollectionUntil<E, C extends Collection<E>>
        extends ObjectUntil<C>
        permits CollectionAwait, CollectionAwait.AfterUpTo,
                SequencedCollectionUntil,
                CollectionStageAdapters.CollectionTerminalStage {

    C until(StructuralCondition condition);

    C until(ExplainedStructuralCondition condition);
}
