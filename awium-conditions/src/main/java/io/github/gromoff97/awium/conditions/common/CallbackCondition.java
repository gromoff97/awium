package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

record CallbackCondition<INPUT, RESULT>(String description,
        Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) implements Condition<INPUT, RESULT> {
    CallbackCondition {
        requireNonNull(description, "description must not be null");
        if (description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        requireNonNull(factory, "evaluator factory must not be null");
    }

    CallbackCondition(String description, AwaitAttempt<? super INPUT, ? extends RESULT> evaluation) {
        this(description, () -> evaluation);
        requireNonNull(evaluation, "evaluation must not be null");
    }

    @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception {
        return requireNonNull(factory.call(), "evaluator must not be null");
    }

    @Override public AttemptResult<RESULT> probe(INPUT actual) throws Throwable {
        return newAttempt().probe(actual).mapValue(value -> value);
    }
}
