package io.github.gromoff97.assertility;

import java.util.Optional;

public sealed interface OptionalUntil<T> extends ObjectUntil<Optional<T>>
        permits OptionalAwait, OptionalAwait.AfterUpTo,
                OptionalStageAdapters.OptionalTerminalStage {

    T until(Present condition);

    T until(ExplainedPresent condition);
}
