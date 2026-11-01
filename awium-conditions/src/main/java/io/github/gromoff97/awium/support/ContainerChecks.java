package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class ContainerChecks {
    private ContainerChecks() {}

    public static <INPUT> AwaitAttempt<INPUT, INPUT> empty(
            String subject, Predicate<? super INPUT> empty, boolean expectEmpty) {
        return actual -> actual == null ? unsatisfied(subject + " was null")
                : empty.test(actual) == expectEmpty ? satisfied(actual)
                        : unsatisfied(subject + (expectEmpty ? " was not empty" : " was empty"));
    }

    public static <ELEMENT> ConditionResult<ELEMENT> single(String subject, Iterable<? extends ELEMENT> values) {
        if (values == null) return unsatisfied(subject + " was null");
        var iterator = values.iterator();
        if (!iterator.hasNext()) return unsatisfied(subject + " size was 0");
        ELEMENT value = iterator.next();
        return iterator.hasNext() ? unsatisfied(subject + " size was greater than 1") : satisfied(value);
    }
}
