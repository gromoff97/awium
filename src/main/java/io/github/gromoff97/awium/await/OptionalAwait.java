package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.await.stages.OptionalAwaitStage;
import io.github.gromoff97.awium.conditioning.conditions.PresentCondition;

import java.time.Duration;
import java.util.Optional;

public sealed interface OptionalAwait<T> extends Await<Optional<T>> permits OptionalAwaitStage {

    @Override
    OptionalAwait<T> every(Duration interval);

    @Override
    OptionalAwait<T> upTo(Duration timeout);

    @Override
    OptionalAwait<T> stableFor(Duration stability);

    T until(PresentCondition condition);

    T until(PresentCondition.ExplainedCondition condition);
}
