package io.github.gromoff97.awium.conditions.collection;

import io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

record NestedCondition<INPUT, VALUE, RESULT>(String selection, AwaitCondition nested, AwaitCondition extraction) implements Condition<INPUT, RESULT> {
    NestedCondition { requireNonNull(nested, "condition must not be null"); }

    static <INPUT, VALUE> Condition<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, AwaitCondition extraction) {
        return new NestedCondition<INPUT, VALUE, VALUE>(selection, nested, extraction);
    }

    static <INPUT, VALUE, RESULT> Condition<INPUT, RESULT> compose(
            String selection, Condition<? super VALUE, RESULT> nested, AwaitCondition extraction) {
        return new NestedCondition<INPUT, VALUE, RESULT>(selection, nested, extraction);
    }

    @Override public String description() { return selection + ": " + nested.description(); }
    @Override public String explanation() { return nested.explanation(); }
    @Override public List<Reference<?>> references() {
        return concat(extraction.references().stream(), nested.references().stream()).toList();
    }
    @Override public ConditionResult<RESULT> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual).mapValue(value -> value); }
    @Override public AwaitAttempt<INPUT, RESULT> newAttempt() {
        var extractionRun = ConditionExecution.<INPUT, VALUE>bind(extraction);
        var evaluator = ConditionExecution.<VALUE, RESULT>bind(nested);
        return actual -> switch (extractionRun.probe(actual)) {
            case Satisfied<? extends VALUE> success -> {
                var result = evaluator.probeAt(success.value(), selection);
                yield result instanceof Unsatisfied<?> failure
                        ? unsatisfied(failure.mismatch() == null ? selection : selection + ": " + failure.mismatch(), failure.cause())
                        : result;
            }
            case Unsatisfied<?> failure -> unsatisfied(failure.mismatch(), failure.cause());
        };
    }
}
