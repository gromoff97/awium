package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.support.SizeRelation;
import java.util.Map;

final class HasSizeCondition extends PreservingCondition<Map<?, ?>> {
    HasSizeCondition(SizeRelation relation, int lower, int upper) {
        super("map size " + relation.description(lower, upper), null,
                relation.check("map", "size", Map::size, lower, upper));
    }
}
