package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedStage;

public sealed interface AwaitCondition permits ConditionStage, ExpectedStage, SelectedStage, SelectedSequenceStage {
}
