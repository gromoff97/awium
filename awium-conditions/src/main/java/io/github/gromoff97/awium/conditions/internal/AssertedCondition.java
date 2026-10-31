package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.CheckedConsumer;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

public final class AssertedCondition<INPUT> extends PreservingCondition<INPUT> {
    public AssertedCondition(CheckedConsumer<? super INPUT> assertion) {
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
