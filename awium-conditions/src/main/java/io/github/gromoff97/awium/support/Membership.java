package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.function.checked.CheckedBiPredicate;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.support.ValueMatching.firstMissingExpectedIndex;
import static io.github.gromoff97.awium.support.ValueMatching.firstPresentExpectedIndex;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public enum Membership {
    ALL, ANY, NONE, NOT_ALL;

    public String operand() { return this == NONE || this == NOT_ALL ? "Unexpected" : "Expected"; }

    public String description(String subject, String items) {
        return subject + switch (this) {
            case ALL -> " contains all expected " + items;
            case ANY -> items.equals("entries") ? " contains an expected entry" : " contains an expected element";
            case NONE -> " does not contain any unexpected " + items;
            case NOT_ALL -> " does not contain all unexpected " + items;
        };
    }

    public <INPUT, ACTUAL, EXPECTED> ConditionResult<INPUT> probe(INPUT actual, String subject, String items,
            List<? extends EXPECTED> expected, Function<? super INPUT, ? extends Collection<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) throws Throwable {
        if (actual == null) return unsatisfied(subject + " was null");
        Collection<? extends ACTUAL> values = expected.isEmpty() ? List.of() : elements.apply(actual);
        int index = this == ALL || this == NOT_ALL
                ? firstMissingExpectedIndex(values, expected, matches) : firstPresentExpectedIndex(values, expected, matches);
        boolean matched = this == ALL || this == NONE ? index < 0 : index >= 0;
        return matched ? satisfied(actual) : unsatisfied(subject + switch (this) {
            case ALL -> " did not contain all expected " + items + "; first missing at expected index " + index;
            case NONE -> " contained unexpected " + items + "; first at unexpected index " + index;
            case NOT_ALL -> " contained all unexpected " + items;
            case ANY -> items.equals("entries") ? " did not contain an expected entry" : " did not contain an expected element";
        });
    }
}
