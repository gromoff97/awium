package io.github.gromoff97.awium.conditions.common.internal;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.conditions.function.CheckedBiPredicate;
import java.util.function.Function;

import static io.github.gromoff97.awium.conditions.common.internal.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class ContainsCondition<INPUT, ACTUAL, EXPECTED> extends PreservingCondition<INPUT> {
    public ContainsCondition(String subject, String element, EXPECTED expected, boolean negated,
            Function<? super INPUT, ? extends Iterable<? extends ACTUAL>> elements,
            CheckedBiPredicate<? super ACTUAL, ? super EXPECTED> matches) {
        super(subject + (negated ? " does not contain unexpected " : " contains expected ") + element,
                new Reference<>(negated ? "Unexpected" : "Expected", expected), actual -> actual == null
                        ? unsatisfied(subject + " was null")
                        : matchesAny(elements.apply(actual), value -> matches.test(value, expected)) != negated
                                ? satisfied(actual) : unsatisfied(subject + (negated ? " contained unexpected " : " did not contain expected ") + element));
    }

}
