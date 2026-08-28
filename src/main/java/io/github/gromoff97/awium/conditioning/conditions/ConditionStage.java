package io.github.gromoff97.awium.conditioning.conditions;

public sealed interface ConditionStage<S, R> extends AwaitCondition
        permits ConditionStage.ResultStage, Condition.PreservingStage {

    sealed interface ResultStage<S, R> extends ConditionStage<S, R> permits Condition {
    }
}
