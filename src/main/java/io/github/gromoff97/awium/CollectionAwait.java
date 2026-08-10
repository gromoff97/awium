package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;
import java.time.Duration;
import java.util.Collection;

public sealed interface CollectionAwait<E, C extends Collection<E>>
        extends ObjectAwait.Until<C>
        permits CollectionStages.CollectionInitialStage {

    AfterEvery<E, C> every(Duration interval);

    AfterUpTo<E, C> upTo(Duration timeout);

    Until<E, C> stableFor(Duration stability);

    C until(StructuralCondition condition);

    C until(StructuralCondition.ExplainedCondition condition);

    sealed interface Until<E, C extends Collection<E>>
            extends ObjectAwait.Until<C>
            permits AfterUpTo, SequencedCollectionAwait,
                    SequencedCollectionAwait.Until,
                    CollectionStages.CollectionTerminalStage {

        C until(StructuralCondition condition);

        C until(StructuralCondition.ExplainedCondition condition);
    }

    sealed interface AfterEvery<E, C extends Collection<E>>
            extends AfterUpTo<E, C>
            permits CollectionStages.CollectionAfterEveryStage {

        AfterUpTo<E, C> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<E, C extends Collection<E>>
            extends Until<E, C>
            permits AfterEvery,
                    CollectionStages.CollectionAfterUpToStage {

        Until<E, C> stableFor(Duration stability);
    }
}
