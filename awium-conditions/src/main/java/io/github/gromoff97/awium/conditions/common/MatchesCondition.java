package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import io.github.gromoff97.awium.result.ConditionResult;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record MatchesCondition<INPUT>(CheckedPredicate<? super INPUT> predicate) implements PreservingCondition<INPUT> {
    MatchesCondition { requireNonNull(predicate, "predicate must not be null"); }

    @Override public String description() { return "value matches"; }

    @Override
    public ConditionResult<?> probe(INPUT actual) throws Throwable {
        return predicate.test(actual) ? satisfied(actual) : unsatisfied("value did not match");
    }
}
