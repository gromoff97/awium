package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.support.SizeRelation;

final class HasLengthCondition extends PreservingCondition<CharSequence> {
    HasLengthCondition(SizeRelation relation, int lower, int upper) {
        super("string length " + relation.description(lower, upper), null,
                relation.check("string", "length", CharSequence::length, lower, upper));
    }
}
