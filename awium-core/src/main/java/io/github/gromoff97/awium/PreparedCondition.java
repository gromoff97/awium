package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.concurrent.Callable;

record PreparedCondition<INPUT, RESULT>(AwaitCondition definition,
        Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) implements Condition<INPUT, RESULT> {
    @Override public String description() { return definition.description(); }
    @Override public String explanation() { return definition.explanation(); }
    @Override public List<Reference<?>> references() { return definition.references(); }
    @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return factory.call(); }
    @Override public AttemptResult<RESULT> probe(INPUT actual) throws Throwable {
        return newAttempt().probe(actual).mapValue(value -> value);
    }
}
