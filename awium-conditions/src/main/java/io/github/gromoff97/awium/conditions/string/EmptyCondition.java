package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record EmptyCondition(boolean expectEmpty) implements PreservingCondition<CharSequence> {
    @Override
    public String description() {
        if (expectEmpty) {
            return "string is empty";
        }
        return "string is not empty";
    }

    @Override
    public AttemptResult<?> probe(CharSequence actual) {
        if (actual == null) {
            return unsatisfied("string was null");
        }
        if (actual.isEmpty() == expectEmpty) {
            return satisfied(actual);
        }
        if (expectEmpty) {
            return unsatisfied("string was not empty");
        }
        return unsatisfied("string was empty");
    }
}
