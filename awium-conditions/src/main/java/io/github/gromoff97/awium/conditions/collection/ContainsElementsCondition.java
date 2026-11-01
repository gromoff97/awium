package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedBiPredicate;
import io.github.gromoff97.awium.support.Membership;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.support.ExpectedValues.snapshot;
import static java.util.Locale.ROOT;

final class ContainsElementsCondition<INPUT, ACTUAL, EXPECTED> extends PreservingCondition<INPUT> {
    ContainsElementsCondition(String subject, String items, Iterable<? extends EXPECTED> expected,
            Membership membership, Function<? super INPUT, ? extends Collection<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        this(subject, items, snapshot(expected, membership.operand().toLowerCase(ROOT) + " " + items), membership, elements, matches);
    }

    private ContainsElementsCondition(String subject, String items, List<? extends EXPECTED> expected,
            Membership membership, Function<? super INPUT, ? extends Collection<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        super(membership.description(subject, items), new Reference<>(membership.operand(), expected),
                actual -> membership.probe(actual, subject, items, expected, elements, matches));
    }
}
