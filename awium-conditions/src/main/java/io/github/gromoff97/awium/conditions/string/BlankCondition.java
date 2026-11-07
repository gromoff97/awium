package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;

record BlankCondition(boolean expectBlank) implements PreservingCondition<CharSequence> {
    @Override
    public String description() {
        if (expectBlank) {
            return "string is blank";
        }
        return "string is not blank";
    }

    @Override
    public AttemptResult<?> probe(CharSequence actual) throws Throwable {
        if (actual == null) return unsatisfied("string was null");
        if (actual.codePoints().sequential().allMatch(Character::isWhitespace) == expectBlank) {
            return satisfied(actual);
        }
        if (expectBlank) {
            return unsatisfied("string was not blank");
        }
        return unsatisfied("string was blank");
    }
}
