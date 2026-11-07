package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static java.util.Objects.requireNonNull;

record ConditionBinding<INPUT, RESULT>(AwaitCondition definition) implements Condition<INPUT, RESULT> {
    ConditionBinding { requireNonNull(definition, "condition must not be null"); }
    @Override public String description() { return definition.description(); }
    @Override public String explanation() { return definition.explanation(); }
    @Override public List<Reference<?>> references() { return definition.references(); }
    @SuppressWarnings("unchecked")
    @Override public Condition<INPUT, RESULT> newRun() {
        return (Condition<INPUT, RESULT>) requireNonNull(definition.newRun(), "condition returned null run");
    }
    @Override public AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception {
        return newRun().newAttempt();
    }
    @Override public AttemptResult<RESULT> probe(INPUT actual) throws Throwable {
        return newAttempt().probe(actual).mapValue(value -> value);
    }

    @SuppressWarnings("unchecked")
    static <INPUT, RESULT> Condition<INPUT, RESULT> bind(AwaitCondition definition) {
        requireNonNull(definition, "condition must not be null");
        if (definition instanceof Condition<?, ?> executable) return (Condition<INPUT, RESULT>) executable;
        return new ConditionBinding<>(definition);
    }

    static Condition<?, ?> metadata(AwaitCondition definition) {
        return ConditionBinding.<Object, Object>bind(definition);
    }
}
