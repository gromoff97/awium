package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.support.Quantifier;
import java.util.function.Function;

import static io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

final class QuantifiedCondition<INPUT, ELEMENT> extends PreservingCondition<INPUT> {
    QuantifiedCondition(String subject, String items, Quantifier quantifier,
            ApplicableCondition<? super ELEMENT, ?> nested,
            Function<? super INPUT, ? extends Iterable<? extends ELEMENT>> elements) {
        super(nested, quantifier.description(subject + " " + items), () -> {
            ConditionExecution<ELEMENT, ?> evaluator = bind(nested);
            return actual -> actual == null ? unsatisfied(subject + " was null")
                    : quantifier.probe(actual, elements, evaluator, quantifier.mismatch(subject + " " + items), subject + " " + items);
        });
    }
}
