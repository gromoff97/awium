package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.await.stages.AwaitStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;

import java.time.Duration;

public sealed interface Await<S> permits AwaitStage, OptionalAwait, StructuralAwait {

    Await<S> every(Duration interval);

    Await<S> upTo(Duration timeout);

    Await<S> stableFor(Duration stability);

    S until(PreservingCondition<? super S> condition);

    S until(PreservingCondition.ExplainedCondition<? super S> condition);

    <R> R until(Condition<? super S, ? extends R> condition);

    <R> R until(
            Condition.ExplainedCondition<? super S, ? extends R> condition);
}
