package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.List;

import static io.github.gromoff97.awium.internal.DiagnosticText.renderValue;
import static java.util.Objects.requireNonNull;

public interface AwaitCondition {
    String description();
    default String explanation() { return null; }
    default List<Reference<?>> references() { return List.of(); }
    AwaitCondition because(String explanation);
    Condition<?, ?> newRun();

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

    record Reference<VALUE>(String label, VALUE value) {
        public Reference {
            requireNonNull(label, "reference label must not be null");
            if (label.isBlank()) throw new IllegalArgumentException("reference label must not be blank");
        }
        @Override public String toString() { return label + "=" + renderValue(value); }
    }
}
