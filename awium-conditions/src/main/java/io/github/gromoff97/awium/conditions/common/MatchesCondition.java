package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import io.github.gromoff97.awium.result.AttemptResult;

import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.requireNonNull;

record MatchesCondition<INPUT>(CheckedPredicate<? super INPUT> predicate) implements PreservingCondition<INPUT> {
    MatchesCondition { requireNonNull(predicate, "predicate must not be null"); }

    @Override public String description() { return "value matches"; }

    @Override
    public AttemptResult<?> probe(INPUT actual) throws Throwable {
        if (predicate.test(actual)) {
            return satisfied(actual);
        }
        return unsatisfied("value did not match");
    }
}
