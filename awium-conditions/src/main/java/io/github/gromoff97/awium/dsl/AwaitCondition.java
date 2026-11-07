package io.github.gromoff97.awium.dsl;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public interface AwaitCondition {
    String description();
    default String explanation() { return null; }
    default List<Reference<?>> references() { return List.of(); }
    AwaitCondition because(String explanation);
    Condition<?, ?> newRun();

    static Optional<AwaitCondition> definitionOf(Condition<?, ?> condition) {
        requireNonNull(condition, "condition must not be null");
        if (condition instanceof ConditionBinding<?, ?> binding) return Optional.of(binding.definition());
        if (condition instanceof AwaitCondition definition) return Optional.of(definition);
        return Optional.empty();
    }

    static <INPUT, RESULT> AwaitAttempt<INPUT, RESULT> bind(TypedCondition<? super INPUT, ? extends RESULT> condition) {
        return io.github.gromoff97.awium.function.AwaitAttempt.bind(condition);
    }

    static <INPUT> AwaitAttempt<INPUT, ?> bind(AwaitCondition condition) {
        return io.github.gromoff97.awium.function.AwaitAttempt.bind(ConditionBinding.<INPUT, Object>bind(condition));
    }

    interface ApplicableCondition<INPUT, REFERENCE> extends AwaitCondition {
        AttemptResult<?> probe(INPUT actual) throws Throwable;
        default AwaitAttempt<? super INPUT, ?> newAttempt() throws Exception { return this::probe; }
        @Override default Condition<INPUT, ?> newRun() {
            return new PreparedCondition<>(this, this::newAttempt);
        }
        @Override ApplicableCondition<INPUT, REFERENCE> because(String explanation);
    }

    interface InputCondition<INPUT> extends ApplicableCondition<INPUT, Reference<?>> {
        @Override InputCondition<INPUT> because(String explanation);
    }

    interface PreservesObservation<INPUT, REFERENCE> extends ApplicableCondition<INPUT, REFERENCE> {
        @Override default Condition<INPUT, ?> newRun() {
            return new PreparedCondition<INPUT, INPUT>(this, () -> {
                var evaluation = requireNonNull(newAttempt(), "evaluator must not be null");
                return actual -> evaluation.probe(actual).mapValue(ignored -> actual);
            });
        }
    }

    interface PreservingCondition<INPUT> extends InputCondition<INPUT>, PreservesObservation<INPUT, Reference<?>> {
        @Override default PreservingCondition<INPUT> because(String explanation) {
            return new Explained.Preserving<>(this, explanation);
        }
    }

    interface ComparisonCondition<EXPECTED> extends PreservesObservation<Object, Reference<? super EXPECTED>> {
        @Override default ComparisonCondition<EXPECTED> because(String explanation) {
            return new Explained.Comparison<>(this, explanation);
        }
    }

    interface SelectedCondition<INPUT> extends InputCondition<INPUT> {
        @Override default SelectedCondition<INPUT> because(String explanation) {
            return new Explained.Selected<>(this, explanation);
        }
    }

    interface MapValueCondition<INPUT> extends InputCondition<INPUT> {
        @Override default MapValueCondition<INPUT> because(String explanation) {
            return new Explained.MapValue<>(this, explanation);
        }
    }

    interface CollectingCondition<CHECK extends AwaitCondition> extends AwaitCondition {
        default List<AwaitCondition> stages() { return List.of(this); }
        @Override default CollectingCondition<CHECK> because(String explanation) {
            return new Explained.Collecting<>(this, explanation);
        }
    }

}
