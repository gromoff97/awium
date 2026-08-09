package io.github.gromoff97.assertility;

public sealed interface ObjectUntil<T>
        permits ObjectAwait, ObjectAwait.AfterUpTo, OptionalUntil,
                CollectionUntil, MapUntil,
                ObjectStageAdapters.ObjectTerminalStage {

    T until(PreservingCondition<? super T> condition);

    T until(ExplainedPreservingCondition<? super T> condition);

    <R> R until(Condition<? super T, ? extends R> condition);

    <R> R until(ExplainedCondition<? super T, ? extends R> condition);
}
