package io.github.gromoff97.awium;

import java.time.Duration;
import java.util.Optional;

public sealed interface OptionalAwait<T> extends ObjectAwait.Until<Optional<T>>
        permits OptionalStages.OptionalInitialStage {

    AfterEvery<T> every(Duration interval);

    AfterUpTo<T> upTo(Duration timeout);

    Until<T> stableFor(Duration stability);

    T until(Present condition);

    T until(Present.Explained condition);

    sealed interface Until<T> extends ObjectAwait.Until<Optional<T>>
            permits AfterUpTo, OptionalStages.OptionalTerminalStage {

        T until(Present condition);

        T until(Present.Explained condition);
    }

    sealed interface AfterEvery<T> extends AfterUpTo<T>
            permits OptionalStages.OptionalAfterEveryStage {

        AfterUpTo<T> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<T> extends Until<T>
            permits AfterEvery, OptionalStages.OptionalAfterUpToStage {

        Until<T> stableFor(Duration stability);
    }
}
