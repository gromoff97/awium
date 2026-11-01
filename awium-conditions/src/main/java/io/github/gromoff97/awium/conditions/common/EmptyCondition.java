package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class EmptyCondition<INPUT> extends PreservingCondition<INPUT> {
    public EmptyCondition(String subject, Predicate<? super INPUT> empty, boolean expectEmpty) {
        super(subject + (expectEmpty ? " is empty" : " is not empty"), null, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : empty.test(actual) == expectEmpty ? satisfied(actual)
                        : unsatisfied(subject + (expectEmpty ? " was not empty" : " was empty")));
    }

}
