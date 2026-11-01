package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import java.util.Map;

import static io.github.gromoff97.awium.conditions.common.internal.SingleElementCondition.single;

final class SingleEntryCondition extends SelectedCondition<Map<?, ?>> {
    SingleEntryCondition() {
        super("map has a single entry", null, actual -> single("map", actual == null ? null : actual.entrySet()));
    }

}
