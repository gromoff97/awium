package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedConsumer;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record AssertedCondition<INPUT>(CheckedConsumer<? super INPUT> assertion) implements PreservingCondition<INPUT> {
    AssertedCondition { requireNonNull(assertion, "assertion must not be null"); }

    @Override public String description() { return "value satisfies assertion"; }

    @Override
    public ConditionResult<?> probe(INPUT actual) throws Throwable {
        try {
            assertion.accept(actual);
            return satisfied(actual);
        } catch (AssertionError error) {
            return unsatisfied("value did not satisfy assertion", error);
        }
    }
}
