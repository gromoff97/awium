package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.support.SizeRelation;
import java.util.Collection;

final class HasSizeCondition extends PreservingCondition<Collection<?>> {
    HasSizeCondition(SizeRelation relation, int lower, int upper) {
        super("collection size " + relation.description(lower, upper), null,
                relation.check("collection", "size", Collection::size, lower, upper));
    }
}
