package io.github.gromoff97.awium.conditions.string;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;

import static io.github.gromoff97.awium.support.ContainerChecks.empty;

final class EmptyCondition extends PreservingCondition<CharSequence> {
    EmptyCondition(boolean expectEmpty) {
        super("string" + (expectEmpty ? " is empty" : " is not empty"), null,
                empty("string", CharSequence::isEmpty, expectEmpty));
    }
}
