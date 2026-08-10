package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.await.stages.OptionalAwaitStage;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;

import java.time.Duration;
import java.util.Optional;

public sealed interface OptionalAwait<T> extends Await.Until<Optional<T>>
        permits OptionalAwaitStage {

    AfterEvery<T> every(Duration interval);

    AfterUpTo<T> upTo(Duration timeout);

    Until<T> stableFor(Duration stability);

    T until(PresentCondition condition);

    T until(PresentCondition.ExplainedCondition condition);

    sealed interface Until<T> extends Await.Until<Optional<T>>
            permits AfterUpTo, OptionalAwaitStage {

        T until(PresentCondition condition);

        T until(PresentCondition.ExplainedCondition condition);
    }

    sealed interface AfterEvery<T> extends AfterUpTo<T>
            permits OptionalAwaitStage {

        AfterUpTo<T> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<T> extends Until<T>
            permits AfterEvery, OptionalAwaitStage {

        Until<T> stableFor(Duration stability);
    }
}
