package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.Optional;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class AbsentCondition extends PreservingCondition<Optional<?>> {
    public AbsentCondition() {
        super("optional is absent", null, actual -> actual == null ? unsatisfied("optional was null")
                : actual.isEmpty() ? satisfied(actual) : unsatisfied("optional was present"));
    }

}
