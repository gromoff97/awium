package io.github.gromoff97.awium.conditions.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.conditions.internal.ExpectedValues.snapshot;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.firstMissingExpectedIndex;
import static io.github.gromoff97.awium.conditions.internal.ValueMatching.firstPresentExpectedIndex;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class ContainsElementsCondition<INPUT, ACTUAL, EXPECTED> extends PreservingCondition<INPUT> {
    public enum Membership { ALL, ANY, NONE, NOT_ALL }

    public ContainsElementsCondition(String subject, String items, Iterable<? extends EXPECTED> expected,
            Membership membership, Function<? super INPUT, ? extends Collection<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        this(subject, items, snapshot(expected, (membership == Membership.NONE || membership == Membership.NOT_ALL
                ? "unexpected " : "expected ") + items), membership, elements, matches);
    }

    private ContainsElementsCondition(String subject, String items, List<? extends EXPECTED> expected,
            Membership membership, Function<? super INPUT, ? extends Collection<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        super(subject + switch (membership) {
                    case ALL -> " contains all expected " + items;
                    case ANY -> items.equals("entries") ? " contains an expected entry" : " contains an expected element";
                    case NONE -> " does not contain any unexpected " + items;
                    case NOT_ALL -> " does not contain all unexpected " + items;
                }, new Reference<>(membership == Membership.NONE || membership == Membership.NOT_ALL ? "Unexpected" : "Expected", expected), actual -> {
            if (actual == null) return unsatisfied(subject + " was null");
            Collection<? extends ACTUAL> values = expected.isEmpty() ? List.of() : elements.apply(actual);
            int index = membership == Membership.ALL || membership == Membership.NOT_ALL
                    ? firstMissingExpectedIndex(values, expected, matches) : firstPresentExpectedIndex(values, expected, matches);
            boolean matched = membership == Membership.ALL || membership == Membership.NONE ? index < 0 : index >= 0;
            return matched ? satisfied(actual) : unsatisfied(subject + switch (membership) {
                case ALL -> " did not contain all expected " + items + "; first missing at expected index " + index;
                case NONE -> " contained unexpected " + items + "; first at unexpected index " + index;
                case NOT_ALL -> " contained all unexpected " + items;
                case ANY -> items.equals("entries") ? " did not contain an expected entry" : " did not contain an expected element";
            });
        });
    }

}
