package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

final class BlankCondition extends PreservingCondition<CharSequence> {
    BlankCondition(boolean expectBlank) {
        super(expectBlank ? "string is blank" : "string is not blank", null, actual -> actual == null
                ? unsatisfied("string was null") : actual.codePoints().sequential().allMatch(Character::isWhitespace) == expectBlank
                        ? satisfied(actual) : unsatisfied(expectBlank ? "string was not blank" : "string was blank"));
    }

}
