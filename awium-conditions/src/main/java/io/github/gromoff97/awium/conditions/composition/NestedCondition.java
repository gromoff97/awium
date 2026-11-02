package io.github.gromoff97.awium.conditions.composition;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.Unsatisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

record NestedCondition<INPUT, RESULT>(String selection, AwaitCondition nested, AwaitCondition extraction) implements Condition<INPUT, RESULT> {
    NestedCondition { requireNonNull(nested, "condition must not be null"); }

    @Override public String description() { return selection + ": " + nested.description(); }
    @Override public String explanation() { return nested.explanation(); }
    @Override public List<Reference<?>> references() {
        return concat(extraction.references().stream(), nested.references().stream()).toList();
    }
    @Override public AttemptResult<RESULT> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual).mapValue(value -> value); }
    @SuppressWarnings("unchecked")
    @Override public AwaitAttempt<INPUT, RESULT> newAttempt() {
        var extractionRun = AwaitAttempt.<INPUT>bind(extraction);
        var evaluator = (AwaitAttempt<Object, RESULT>) AwaitAttempt.<Object>bind(nested);
        return actual -> switch (extractionRun.probe(actual)) {
            case Satisfied<?> success -> {
                var result = evaluator.probeAt(success.value(), selection);
                if (result instanceof Unsatisfied<?> failure) {
                    yield unsatisfied(failure.diagnostic().withMismatch(
                            selection + ": " + failure.mismatch()), failure.cause());
                }
                yield result;
            }
            case Unsatisfied<?> failure -> unsatisfied(failure.diagnostic(), failure.cause());
        };
    }
}
