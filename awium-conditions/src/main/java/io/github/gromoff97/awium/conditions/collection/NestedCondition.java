package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.support.NestedChecks;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

final class NestedCondition<INPUT, RESULT> extends DefinedCondition<INPUT, RESULT> {

    static <INPUT, VALUE> Condition<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, AwaitCondition extractionCondition) {
        return new NestedCondition<>(selection, nested, extractionCondition,
                () -> ConditionExecution.<VALUE, VALUE>bind(nested));
    }

    static <INPUT, VALUE, RESULT> Condition<INPUT, RESULT> compose(
            String selection, Condition<? super VALUE, RESULT> nested, AwaitCondition extractionCondition) {
        return new NestedCondition<>(selection, nested, extractionCondition,
                () -> bind(nested));
    }

    private <VALUE> NestedCondition(String selection, AwaitCondition nested,
            AwaitCondition extractionCondition,
            Supplier<? extends ConditionExecution<? super VALUE, ? extends RESULT>> evaluatorFactory) {
        super(nested, selection,
                concat(extractionCondition.references().stream(), requireNonNull(nested, "condition must not be null").references().stream()).toList(),
                NestedChecks.<INPUT, VALUE, RESULT>evaluation(extractionCondition, evaluatorFactory, selection));
    }
}
