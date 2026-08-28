package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.NarrowingStage;

public sealed interface AwaitCondition permits ConditionStage, ExpectedStage, ExpectedSequenceStage, NarrowingStage,
        SelectedStage, SelectedSequenceStage {
}
