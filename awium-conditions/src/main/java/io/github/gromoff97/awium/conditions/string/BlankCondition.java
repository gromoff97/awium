package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record BlankCondition(boolean expectBlank) implements PreservingCondition<CharSequence> {
    @Override public String description() { return expectBlank ? "string is blank" : "string is not blank"; }

    @Override
    public AttemptResult<?> probe(CharSequence actual) throws Throwable {
        if (actual == null) return unsatisfied("string was null");
        return actual.codePoints().sequential().allMatch(Character::isWhitespace) == expectBlank ? satisfied(actual)
                : unsatisfied(expectBlank ? "string was not blank" : "string was blank");
    }
}
