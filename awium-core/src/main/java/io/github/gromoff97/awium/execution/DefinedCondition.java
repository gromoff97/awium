package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public class DefinedCondition<INPUT, RESULT> extends AwaitCondition.CheckDefinition<INPUT, RESULT>
        implements Condition<INPUT, RESULT> {
    public DefinedCondition(String description, Reference<?> reference,
            AwaitAttempt<? super INPUT, ? extends RESULT> evaluation) {
        this(description, reference, () -> evaluation);
        requireNonNull(evaluation, "evaluation must not be null");
    }

    public DefinedCondition(String description, Reference<?> reference,
            Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) {
        super(description, reference == null ? List.of() : List.of(reference), null, factory);
    }

    public DefinedCondition(AwaitCondition nested, String description, List<Reference<?>> references,
            Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) {
        super(description + ": " + requireNonNull(nested, "condition must not be null").description(), references,
                nested.explanation(), factory);
    }

    @Override
    public ConditionResult<RESULT> probe(INPUT actual) throws Throwable {
        return newAttempt().probe(actual).mapValue(value -> value);
    }

}
