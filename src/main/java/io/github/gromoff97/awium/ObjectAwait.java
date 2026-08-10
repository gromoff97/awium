package io.github.gromoff97.awium;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import java.time.Duration;

public sealed interface ObjectAwait<T>
        permits ObjectStages.ObjectInitialStage {

    AfterEvery<T> every(Duration interval);

    AfterUpTo<T> upTo(Duration timeout);

    Until<T> stableFor(Duration stability);

    T until(PreservingCondition<? super T> condition);

    T until(PreservingCondition.ExplainedCondition<? super T> condition);

    <R> R until(Condition<? super T, ? extends R> condition);

    <R> R until(Condition.ExplainedCondition<? super T, ? extends R> condition);

    sealed interface Until<T>
            permits AfterUpTo, OptionalAwait, OptionalAwait.Until,
                    CollectionAwait, CollectionAwait.Until,
                    MapAwait, MapAwait.Until,
                    ObjectStages.ObjectTerminalStage {

        T until(PreservingCondition<? super T> condition);

        T until(PreservingCondition.ExplainedCondition<? super T> condition);

        <R> R until(Condition<? super T, ? extends R> condition);

        <R> R until(Condition.ExplainedCondition<? super T, ? extends R> condition);
    }

    sealed interface AfterEvery<T> extends AfterUpTo<T>
            permits ObjectStages.ObjectAfterEveryStage {

        AfterUpTo<T> upTo(Duration timeout);
    }

    sealed interface AfterUpTo<T> extends Until<T>
            permits AfterEvery, ObjectStages.ObjectAfterUpToStage {

        Until<T> stableFor(Duration stability);
    }
}
