package io.github.gromoff97.awium.support;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.concurrent.Callable;

public record TestCondition<INPUT, RESULT>(String description, Reference<?> reference,
        Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) implements Condition<INPUT, RESULT> {
    public TestCondition(String description, Reference<?> reference, AwaitAttempt<? super INPUT, ? extends RESULT> evaluation) {
        this(description, reference, () -> evaluation);
    }

    @Override public List<Reference<?>> references() { return reference == null ? List.of() : List.of(reference); }
    @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return factory.call(); }
    @Override public ConditionResult<RESULT> probe(INPUT actual) throws Throwable { return newAttempt().probe(actual).mapValue(value -> value); }
}
