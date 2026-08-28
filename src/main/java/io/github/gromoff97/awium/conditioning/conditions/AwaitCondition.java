package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;

public sealed interface AwaitCondition permits ConditionStage, SelectedStage, SelectedSequenceStage {
}
