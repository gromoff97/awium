package io.github.gromoff97.awium.condition;

/**
 * A terminal condition shape with an explicit observed-to-result relationship.
 *
 * @param <Observed> value supplied to the condition
 * @param <Result> value returned by {@code until} when the condition is satisfied
 */
public sealed interface ConditionStage<Observed, Result> extends AwaitCondition
        permits ConditionStage.ResultStage, Condition.PreservingStage {

    sealed interface ResultStage<Observed, Result> extends ConditionStage<Observed, Result> permits Condition {
    }
}
