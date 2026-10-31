package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import java.util.Optional;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class PresentCondition extends SelectedCondition<Optional<?>> {
    public PresentCondition() {
        super("optional is present", null, actual -> actual == null ? unsatisfied("optional was null")
                : actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty"));
    }

}
