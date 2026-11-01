package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import java.util.Collection;

import static io.github.gromoff97.awium.support.ContainerChecks.single;

final class SingleElementCondition extends SelectedCondition<Collection<?>> {
    SingleElementCondition() {
        super("collection has a single element", null, actual -> single("collection", actual));
    }

}
