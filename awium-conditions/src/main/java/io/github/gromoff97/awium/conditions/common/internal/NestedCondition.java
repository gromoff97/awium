package io.github.gromoff97.awium.conditions.common.internal;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.DefinedCondition;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import static io.github.gromoff97.awium.execution.ConditionExecution.bind;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

public final class NestedCondition<INPUT, RESULT> extends DefinedCondition<INPUT, RESULT> {

    public static <INPUT, VALUE> Condition<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, AwaitCondition extractionCondition) {
        return new NestedCondition<>(selection, nested, extractionCondition,
                () -> ConditionExecution.<VALUE, VALUE>bind(nested));
    }

    public static <INPUT, VALUE, RESULT> Condition<INPUT, RESULT> compose(
            String selection, Condition<? super VALUE, RESULT> nested, AwaitCondition extractionCondition) {
        return new NestedCondition<>(selection, nested, extractionCondition,
                () -> bind(nested));
    }

    private <VALUE> NestedCondition(String selection, AwaitCondition nested,
            AwaitCondition extractionCondition,
            Supplier<? extends ConditionExecution<? super VALUE, ? extends RESULT>> evaluatorFactory) {
        super(nested, selection,
                concat(extractionCondition.references().stream(), requireNonNull(nested, "condition must not be null").references().stream()).toList(), () -> {
            // Bare selectors obtain their value type from the enclosing selector factory's source constraints.
            var extraction = ConditionExecution.<INPUT, VALUE>bind(extractionCondition);
            var evaluator = evaluatorFactory.get();
            return actual -> switch (extraction.probe(actual)) {
                case Satisfied<? extends VALUE> success -> {
                    var evaluated = evaluator.probeAt(success.value(), selection);
                    yield evaluated instanceof Unsatisfied<?>
                            ? nestedMismatch(selection, evaluated) : evaluated;
                }
                case Unsatisfied<?> mismatch -> unsatisfied(mismatch.mismatch(), mismatch.cause());
            };
        });
    }

    static <RESULT> Unsatisfied<RESULT> nestedMismatch(String mismatch, ConditionResult<?> evaluated) {
        if (!(evaluated instanceof Unsatisfied<?> failure)) return unsatisfied(mismatch);
        return unsatisfied(failure.mismatch() == null ? mismatch : mismatch + ": " + failure.mismatch(), failure.cause());
    }

}
