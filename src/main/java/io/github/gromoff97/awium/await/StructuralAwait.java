package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;

import java.time.Duration;

public sealed interface StructuralAwait<S> extends Await<S> permits StructuralAwaitStage {

    @Override
    StructuralAwait<S> every(Duration interval);

    @Override
    StructuralAwait<S> upTo(Duration timeout);

    @Override
    StructuralAwait<S> stableFor(Duration stability);

    S until(StructuralCondition condition);

    S until(StructuralCondition.ExplainedCondition condition);
}
