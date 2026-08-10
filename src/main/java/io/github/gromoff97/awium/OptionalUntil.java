package io.github.gromoff97.awium;

import java.util.Optional;

public sealed interface OptionalUntil<T> extends ObjectUntil<Optional<T>>
        permits OptionalAwait, OptionalAwait.AfterUpTo,
                OptionalStageAdapters.OptionalTerminalStage {

    T until(Present condition);

    T until(ExplainedPresent condition);
}
