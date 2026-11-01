package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

record EmptyCondition(boolean expectEmpty) implements PreservingCondition<CharSequence> {
    @Override public String description() { return "string" + (expectEmpty ? " is empty" : " is not empty"); }

    @Override
    public ConditionResult<?> probe(CharSequence actual) {
        return actual == null ? unsatisfied("string was null")
                : actual.isEmpty() == expectEmpty ? satisfied(actual)
                        : unsatisfied("string" + (expectEmpty ? " was not empty" : " was empty"));
    }
}
