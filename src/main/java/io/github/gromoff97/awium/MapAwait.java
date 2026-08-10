package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;
import java.time.Duration;
import java.util.Map;

public sealed interface MapAwait<K, V, M extends Map<K, V>>
        extends ObjectAwait.Until<M> permits MapStages.MapInitialStage {

    AfterEvery<K, V, M> every(Duration interval);

    AfterUpTo<K, V, M> upTo(Duration timeout);

    Until<K, V, M> stableFor(Duration stability);

    M until(StructuralCondition condition);

    M until(StructuralCondition.ExplainedCondition condition);

    sealed interface Until<K, V, M extends Map<K, V>>
            extends ObjectAwait.Until<M>
            permits AfterUpTo, MapStages.MapTerminalStage {

        M until(StructuralCondition condition);

        M until(StructuralCondition.ExplainedCondition condition);
    }

    sealed interface AfterEvery<K, V, M extends Map<K, V>>
            extends AfterUpTo<K, V, M>
            permits MapStages.MapAfterEveryStage {

        AfterUpTo<K, V, M> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<K, V, M extends Map<K, V>>
            extends Until<K, V, M>
            permits AfterEvery, MapStages.MapAfterUpToStage {

        Until<K, V, M> stableFor(Duration stability);
    }
}
