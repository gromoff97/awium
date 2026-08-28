package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.fluent.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedStage;
import io.github.gromoff97.awium.fluent.Condition.ExpectedStage;
import io.github.gromoff97.awium.fluent.Condition.ExpectedSequenceStage;
import io.github.gromoff97.awium.fluent.Condition.NarrowingStage;

/** Common sealed root for every condition shape accepted by {@code until}. */
public sealed interface AwaitCondition permits ConditionStage, ExpectedStage, ExpectedSequenceStage, NarrowingStage,
        SelectedStage, SelectedSequenceStage {
}
