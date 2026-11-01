package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.function.checked.CheckedBiPredicate;
import java.util.function.Function;

import static io.github.gromoff97.awium.support.ValueMatching.contains;

final class ContainsCondition<INPUT, ACTUAL, EXPECTED> extends PreservingCondition<INPUT> {
    ContainsCondition(String subject, String element, EXPECTED expected, boolean negated,
            Function<? super INPUT, ? extends Iterable<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        super(subject + (negated ? " does not contain unexpected " : " contains expected ") + element,
                new Reference<>(negated ? "Unexpected" : "Expected", expected),
                actual -> contains(actual, subject, element, expected, negated, elements, matches));
    }
}
