package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AwaitAttempt;
import io.github.gromoff97.awium.internal.DiagnosticText;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public abstract sealed class Condition
        permits Condition.Check, Condition.Captured {

    private final String description;
    private final String explanation;
    private final List<Reference<?>> references;

    private Condition(Condition original) {
        this(required(original).description, original.references, original.explanation);
    }

    private Condition(Condition original, String explanation) {
        this(required(original).description, original.references, nonBlank(explanation, "explanation"));
    }

    private Condition(String description, List<Reference<?>> references, String explanation) {
        this.description = nonBlank(description, "description");
        this.explanation = explanation;
        this.references = List.copyOf(references);
    }

    public final String description() {
        return description;
    }

    public final String explanation() {
        return explanation;
    }

    public abstract Condition because(String explanation);

    public final List<Reference<?>> references() {
        return references;
    }

    static <C extends Condition> C required(C condition) {
        return requireNonNull(condition, "condition must not be null");
    }

    public abstract static sealed class Check extends Condition
            permits ApplicableCondition {

        final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;

        private Check(String description, List<Reference<?>> references,
                Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
            super(description, references, explanation);
            this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        }

        private Check(Check original) {
            super(original);
            evaluatorFactory = original.evaluatorFactory;
        }

        private Check(Check original, String explanation) {
            super(original, explanation);
            evaluatorFactory = original.evaluatorFactory;
        }

        @Override
        public abstract Check because(String explanation);
    }

    public sealed interface PreservesObservation permits PreservingCondition, ComparisonCondition {}

    public abstract static sealed class ApplicableCondition<INPUT, REFERENCE> extends Check
            permits InputCondition, ComparisonCondition {

        private ApplicableCondition(String description, List<Reference<?>> references,
                Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
            super(description, references, evaluatorFactory, explanation);
        }

        private ApplicableCondition(Check original) {
            super(original);
        }

        private ApplicableCondition(ApplicableCondition<INPUT, REFERENCE> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public abstract ApplicableCondition<INPUT, REFERENCE> because(String explanation);
    }

    public abstract static sealed class InputCondition<INPUT>
            extends ApplicableCondition<INPUT, Reference<?>>
            permits Typed, PreservingCondition, SelectedCondition, MapValueCondition {

        private InputCondition(String description, List<Reference<?>> references,
                Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
            super(description, references, evaluatorFactory, explanation);
        }

        private InputCondition(Check original) {
            super(original);
        }

        private InputCondition(InputCondition<INPUT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public abstract InputCondition<INPUT> because(String explanation);
    }

    public abstract static sealed class Captured extends Condition
            permits CapturedSource {

        private final List<Check> stages;

        private Captured(Check[] conditions) {
            super("conditions are satisfied in order", List.of(), null);
            stages = Arrays.stream(requireNonNull(conditions, "conditions must not be null"))
                    .map(Condition::required).toList();
            if (stages.isEmpty()) throw new IllegalArgumentException("conditions must not be empty");
        }

        private Captured(Captured original, String explanation) {
            super(original, explanation);
            stages = original.stages;
        }

        public final List<Check> stages() {
            return stages;
        }

        @Override
        public abstract Captured because(String explanation);
    }

    public static final class Typed<INPUT, RESULT> extends InputCondition<INPUT> {

        Typed(String description, Reference<?> reference,
                Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> evaluatorFactory) {
            super(description, reference == null ? List.of() : List.of(reference), evaluatorFactory, null);
        }

        Typed(Condition nested, String description, List<Reference<?>> references,
                Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> evaluatorFactory) {
            super(description + ": " + required(nested).description, references, evaluatorFactory, nested.explanation);
        }

        private Typed(Typed<INPUT, RESULT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public Typed<INPUT, RESULT> because(String explanation) {
            return new Typed<>(this, explanation);
        }
    }

    public static final class PreservingCondition<INPUT>
            extends InputCondition<INPUT> implements PreservesObservation {

        PreservingCondition(Typed<INPUT, INPUT> definition) {
            super(definition);
        }

        private PreservingCondition(PreservingCondition<INPUT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public PreservingCondition<INPUT> because(String explanation) {
            return new PreservingCondition<>(this, explanation);
        }
    }

    public static final class ComparisonCondition<EXPECTED>
            extends ApplicableCondition<Object, Reference<? super EXPECTED>> implements PreservesObservation {

        ComparisonCondition(Typed<Object, Object> definition) {
            super(definition);
        }

        private ComparisonCondition(ComparisonCondition<EXPECTED> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public ComparisonCondition<EXPECTED> because(String explanation) {
            return new ComparisonCondition<>(this, explanation);
        }
    }

    public static final class SelectedCondition<INPUT>
            extends InputCondition<INPUT> {

        SelectedCondition(Typed<INPUT, ?> definition) {
            super(definition);
        }

        private SelectedCondition(SelectedCondition<INPUT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public SelectedCondition<INPUT> because(String explanation) {
            return new SelectedCondition<>(this, explanation);
        }
    }

    public static final class MapValueCondition<INPUT>
            extends InputCondition<INPUT> {

        MapValueCondition(Typed<INPUT, ?> definition) {
            super(definition);
        }

        private MapValueCondition(MapValueCondition<INPUT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public MapValueCondition<INPUT> because(String explanation) {
            return new MapValueCondition<>(this, explanation);
        }
    }

    public static final class CapturedSource<CHECK extends Check> extends Captured {

        CapturedSource(Check[] conditions) {
            super(conditions);
        }

        private CapturedSource(CapturedSource<CHECK> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public CapturedSource<CHECK> because(String explanation) {
            return new CapturedSource<>(this, explanation);
        }
    }

    public record Reference<VALUE>(String label, VALUE value) {
        public Reference {
            label = nonBlank(label, "reference label");
        }

        @Override
        public String toString() {
            return label + "=" + new DiagnosticText(null).value(value);
        }
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
