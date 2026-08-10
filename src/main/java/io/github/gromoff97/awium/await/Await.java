package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;

import java.time.Duration;

public sealed interface Await<S> permits AwaitStage {

    AfterEvery<S> every(Duration interval);

    AfterUpTo<S> upTo(Duration timeout);

    Until<S> stableFor(Duration stability);

    S until(PreservingCondition<? super S> condition);

    S until(PreservingCondition.ExplainedCondition<? super S> condition);

    <R> R until(Condition<? super S, ? extends R> condition);

    <R> R until(Condition.ExplainedCondition<? super S, ? extends R> condition);

    sealed interface Until<S>
            permits AfterUpTo, OptionalAwait, OptionalAwait.Until,
                    StructuralAwait, StructuralAwait.Until, AwaitStage {

        S until(PreservingCondition<? super S> condition);

        S until(PreservingCondition.ExplainedCondition<? super S> condition);

        <R> R until(Condition<? super S, ? extends R> condition);

        <R> R until(
                Condition.ExplainedCondition<? super S, ? extends R> condition);
    }

    sealed interface AfterEvery<S> extends AfterUpTo<S> permits AwaitStage {

        AfterUpTo<S> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<S> extends Until<S>
            permits AfterEvery, AwaitStage {

        Until<S> stableFor(Duration stability);
    }
}
