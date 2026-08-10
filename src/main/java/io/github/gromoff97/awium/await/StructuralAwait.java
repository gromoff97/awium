package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.await.stages.StructuralAwaitStage;
import io.github.gromoff97.awium.conditioning.conditions.StructuralCondition;

import java.time.Duration;

public sealed interface StructuralAwait<S> extends Await.Until<S>
        permits StructuralAwaitStage {

    AfterEvery<S> every(Duration interval);

    AfterUpTo<S> upTo(Duration timeout);

    Until<S> stableFor(Duration stability);

    S until(StructuralCondition condition);

    S until(StructuralCondition.ExplainedCondition condition);

    sealed interface Until<S> extends Await.Until<S>
            permits AfterUpTo, StructuralAwaitStage {

        S until(StructuralCondition condition);

        S until(StructuralCondition.ExplainedCondition condition);
    }

    sealed interface AfterEvery<S> extends AfterUpTo<S>
            permits StructuralAwaitStage {

        AfterUpTo<S> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<S> extends Until<S>
            permits AfterEvery, StructuralAwaitStage {

        Until<S> stableFor(Duration stability);
    }
}
