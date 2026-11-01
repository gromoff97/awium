package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

final class MatchesCondition<INPUT> extends PreservingCondition<INPUT> {
    MatchesCondition(CheckedPredicate<? super INPUT> predicate) {
        super("value matches", null, actual -> predicate.test(actual) ? satisfied(actual) : unsatisfied("value did not match"));
        requireNonNull(predicate, "predicate must not be null");
    }

}
