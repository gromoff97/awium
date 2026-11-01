package io.github.gromoff97.awium.conditions.map;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.Map;

import static io.github.gromoff97.awium.support.ContainerChecks.empty;

final class EmptyCondition extends PreservingCondition<Map<?, ?>> {
    EmptyCondition(boolean expectEmpty) {
        super("map" + (expectEmpty ? " is empty" : " is not empty"), null,
                empty("map", Map::isEmpty, expectEmpty));
    }
}
