package io.github.gromoff97.awium;

import io.github.gromoff97.awium.execution.CheckRun;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.DiagnosticText;
import io.github.gromoff97.awium.function.AwaitAttempt;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public interface AwaitCondition {
    default String description() {
        String name = getClass().getSimpleName();
        return name.isBlank() ? "condition" : name;
    }

    default String explanation() { return null; }

    default List<Reference<?>> references() { return List.of(); }

    AwaitCondition because(String explanation);

    ConditionRun<?, ?> newRun(AwaitCondition definition, boolean recording);

    interface ApplicableCondition<INPUT, REFERENCE> extends AwaitCondition {
        @Override
        ConditionRun<INPUT, ?> newRun(AwaitCondition definition, boolean recording);

        @Override
        ApplicableCondition<INPUT, REFERENCE> because(String explanation);
    }

    interface InputCondition<INPUT> extends ApplicableCondition<INPUT, Reference<?>> {
        @Override
        InputCondition<INPUT> because(String explanation);
    }

    sealed interface PreservesObservation permits PreservingCondition, ComparisonCondition {}

    abstract class Definition implements AwaitCondition {
        private final String description;
        private final String explanation;
        private final List<Reference<?>> references;

        protected Definition(String description, List<Reference<?>> references, String explanation) {
            this.description = nonBlank(description, "description");
            this.references = List.copyOf(references);
            this.explanation = explanation;
        }

        protected Definition(AwaitCondition original, String explanation) {
            this(requireNonNull(original, "condition must not be null").description(), original.references(),
                    nonBlank(explanation, "explanation"));
        }

        @Override public final String description() { return description; }
        @Override public final String explanation() { return explanation; }
        @Override public final List<Reference<?>> references() { return references; }
    }

    abstract class CheckDefinition<INPUT, RESULT> extends Definition {
        private final Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory;

        protected CheckDefinition(String description, List<Reference<?>> references, String explanation,
                Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> factory) {
            super(description, references, explanation);
            this.factory = requireNonNull(factory, "evaluator factory must not be null");
        }

        protected CheckDefinition(CheckDefinition<INPUT, RESULT> original, String explanation) {
            super(original, explanation);
            factory = original.factory;
        }

        public final AwaitAttempt<? super INPUT, ? extends RESULT> newAttempt() throws Exception {
            return factory.call();
        }

        @Override
        public final ConditionRun<INPUT, RESULT> newRun(AwaitCondition definition, boolean recording) {
            return new CheckRun<>(definition, this::newAttempt, recording);
        }
    }

    non-sealed class PreservingCondition<INPUT> extends CheckDefinition<INPUT, INPUT>
            implements InputCondition<INPUT>, PreservesObservation {
        protected PreservingCondition(String description, Reference<?> reference,
                AwaitAttempt<? super INPUT, ? extends INPUT> evaluation) {
            super(description, reference == null ? List.of() : List.of(reference), null,
                    () -> actual -> evaluation.probe(actual).mapValue(ignored -> actual));
            requireNonNull(evaluation, "evaluation must not be null");
        }

        protected PreservingCondition(AwaitCondition nested, String description,
                Callable<? extends AwaitAttempt<? super INPUT, ? extends INPUT>> factory) {
            super(description + ": " + requireNonNull(nested, "condition must not be null").description(),
                    nested.references(), nested.explanation(), () -> {
                        var evaluation = factory.call();
                        return actual -> evaluation.probe(actual).mapValue(ignored -> actual);
                    });
        }

        private PreservingCondition(PreservingCondition<INPUT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public PreservingCondition<INPUT> because(String explanation) {
            return new PreservingCondition<>(this, explanation);
        }
    }

    non-sealed class ComparisonCondition<EXPECTED> extends CheckDefinition<Object, Object>
            implements ApplicableCondition<Object, Reference<? super EXPECTED>>, PreservesObservation {
        protected ComparisonCondition(String description, Reference<?> reference, AwaitAttempt<Object, Object> evaluation) {
            super(description, List.of(reference), null,
                    () -> actual -> evaluation.probe(actual).mapValue(ignored -> actual));
            requireNonNull(evaluation, "evaluation must not be null");
        }

        private ComparisonCondition(ComparisonCondition<EXPECTED> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public ComparisonCondition<EXPECTED> because(String explanation) {
            return new ComparisonCondition<>(this, explanation);
        }
    }

    class SelectedCondition<INPUT> extends CheckDefinition<INPUT, Object> implements InputCondition<INPUT> {
        protected SelectedCondition(String description, Reference<?> reference, AwaitAttempt<? super INPUT, ?> evaluation) {
            super(description, reference == null ? List.of() : List.of(reference), null, () -> evaluation);
            requireNonNull(evaluation, "evaluation must not be null");
        }

        protected SelectedCondition(Condition<INPUT, ?> definition) {
            super(definition.description(), definition.references(), definition.explanation(), definition::newAttempt);
        }

        private SelectedCondition(SelectedCondition<INPUT> original, String explanation) { super(original, explanation); }

        @Override
        public SelectedCondition<INPUT> because(String explanation) { return new SelectedCondition<>(this, explanation); }
    }

    class MapValueCondition<INPUT> extends CheckDefinition<INPUT, Object> implements InputCondition<INPUT> {
        protected MapValueCondition(String description, Reference<?> reference, AwaitAttempt<? super INPUT, ?> evaluation) {
            super(description, reference == null ? List.of() : List.of(reference), null, () -> evaluation);
            requireNonNull(evaluation, "evaluation must not be null");
        }

        protected MapValueCondition(Condition<INPUT, ?> definition) {
            super(definition.description(), definition.references(), definition.explanation(), definition::newAttempt);
        }

        private MapValueCondition(MapValueCondition<INPUT> original, String explanation) { super(original, explanation); }

        @Override
        public MapValueCondition<INPUT> because(String explanation) { return new MapValueCondition<>(this, explanation); }
    }

    abstract class CollectingCondition<CHECK extends AwaitCondition> extends Definition {
        protected CollectingCondition(String description, List<Reference<?>> references, String explanation) {
            super(description, references, explanation);
        }

        protected CollectingCondition(CollectingCondition<CHECK> original, String explanation) { super(original, explanation); }

        @Override
        public final ConditionRun<?, ?> newRun(AwaitCondition definition, boolean recording) {
            return collectingRun(definition, recording);
        }

        protected abstract <OBSERVED> ConditionRun<OBSERVED, List<OBSERVED>> collectingRun(
                AwaitCondition definition, boolean recording);

        @Override
        public abstract CollectingCondition<CHECK> because(String explanation);
    }

    record Reference<VALUE>(String label, VALUE value) {
        public Reference { label = nonBlank(label, "reference label"); }

        @Override
        public String toString() { return label + "=" + new DiagnosticText(null).value(value); }
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
