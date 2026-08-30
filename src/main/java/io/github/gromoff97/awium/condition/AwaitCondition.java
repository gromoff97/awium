package io.github.gromoff97.awium.condition;

import io.github.gromoff97.awium.condition.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.condition.Condition.SelectedStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedStage;
import io.github.gromoff97.awium.condition.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.condition.Condition.NarrowingStage;

/** Common sealed root for every condition shape accepted by {@code until}. */
public sealed interface AwaitCondition permits ConditionStage, ExpectedStage, ExpectedSequenceStage, NarrowingStage,
        SelectedStage, SelectedSequenceStage {
}
