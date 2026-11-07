package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

public interface TypedCondition<INPUT, RESULT> extends Condition<INPUT, RESULT>, AwaitCondition.InputCondition<INPUT> {
    static <INPUT, RESULT> TypedCondition<INPUT, RESULT> from(Condition<? super INPUT, RESULT> condition) {
        return new CoreConditionAdapter<>(condition);
    }

    @Override default String explanation() { return null; }
    @Override default List<Reference<?>> references() { return List.of(); }

    @Override default List<? extends Condition<?, ?>> historyConditions(Condition<?, ?> definition) {
        AwaitCondition original = this;
        if (definition instanceof ConditionBinding<?, ?> binding) original = binding.definition();
        else if (definition instanceof AwaitCondition candidate) original = candidate;
        final var root = original;
        return historyDefinitions(original).stream()
                .map(stage -> stage == root ? definition : ConditionBinding.metadata(stage)).toList();
    }

    @Override
    AttemptResult<RESULT> probe(INPUT actual) throws Throwable;

    @Override
    default AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception { return this::probe; }

    @Override
    default Condition<INPUT, RESULT> newRun() { return this; }

    default List<? extends AwaitCondition> historyDefinitions(AwaitCondition definition) { return List.of(definition); }

    @Override
    default TypedCondition<INPUT, RESULT> because(String explanation) {
        return new CoreConditionAdapter<>(Condition.super.because(explanation));
    }
}
