package io.github.gromoff97.assertility;

import java.util.Map;

public sealed interface MapUntil<K, V, M extends Map<K, V>>
        extends ObjectUntil<M>
        permits MapAwait, MapAwait.AfterUpTo,
                MapStageAdapters.MapTerminalStage {

    M until(StructuralCondition condition);

    M until(ExplainedStructuralCondition condition);
}
