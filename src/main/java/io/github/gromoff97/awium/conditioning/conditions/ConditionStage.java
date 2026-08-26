package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;

public sealed interface ConditionStage<S, R> permits Condition,
        ConditionRuntime.RuntimeExplainedCondition {
}
