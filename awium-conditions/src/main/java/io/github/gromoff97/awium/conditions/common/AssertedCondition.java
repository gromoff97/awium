package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedConsumer;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

final class AssertedCondition<INPUT> extends PreservingCondition<INPUT> {
    AssertedCondition(CheckedConsumer<? super INPUT> assertion) {
        super("value satisfies assertion", null, actual -> {
            try {
                assertion.accept(actual);
                return satisfied(actual);
            } catch (AssertionError error) {
                return unsatisfied("value did not satisfy assertion", error);
            }
        });
        requireNonNull(assertion, "assertion must not be null");
    }

}
