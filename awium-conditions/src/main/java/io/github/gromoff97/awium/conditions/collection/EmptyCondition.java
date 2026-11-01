package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.Collection;

import static io.github.gromoff97.awium.support.ContainerChecks.empty;

final class EmptyCondition extends PreservingCondition<Collection<?>> {
    EmptyCondition(boolean expectEmpty) {
        super("collection" + (expectEmpty ? " is empty" : " is not empty"), null,
                empty("collection", Collection::isEmpty, expectEmpty));
    }
}
