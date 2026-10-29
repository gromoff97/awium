package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.AwaitAttempt;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/**
 * Immutable check definition and diagnostic metadata.
 * A check's {@code INPUT} bounds the observations it accepts; it does not widen the
 * concrete source type returned by a preserving check or a capture sequence.
 */
public abstract sealed class Condition
        permits Condition.Check, Condition.Captured {

    private final String description;
    private final String explanation;
    private final Reference<?> reference;
    final Condition identity;

    Condition(Condition original) {
        this(required(original).description, original.reference, original.explanation, original.identity);
    }

    Condition(Condition original, String explanation) {
        this(required(original).description, original.reference,
                nonBlank(explanation, "explanation"), original.identity);
    }

    private Condition(String description, Reference<?> reference, String explanation) {
        this(description, reference, explanation, null);
    }

    private Condition(String description, Reference<?> reference, String explanation, Condition identity) {
        this.description = nonBlank(description, "description");
        this.explanation = explanation;
        this.reference = reference;
        this.identity = identity == null ? this : identity;
    }

    public final String description() {
        return description;
    }

    /** Returns the reason for this condition, or null when no reason was supplied. */
    public final String explanation() {
        return explanation;
    }

    /**
     * Returns an independent copy with the supplied reason and the same evaluation.
     * Replaces any earlier reason. Inline generic factories before this call may need
     * a typed lambda parameter or method reference because Java cannot use the terminal's
     * target type to infer their input types through a chained method call.
     *
     * @param explanation non-null, non-blank reason for the condition
     * @return a condition with the same input and result constraints
     * @throws NullPointerException if the explanation is null
     * @throws IllegalArgumentException if the explanation is blank
     */
    public abstract Condition because(String explanation);

    public final Reference<?> reference() {
        return reference;
    }

    static <C extends Condition> C required(C condition) {
        return requireNonNull(condition, "condition must not be null");
    }

    /** A check evaluated against one observation; capture sequences contain these checks. */
    public abstract static sealed class Check extends Condition
            permits ApplicableCondition {

        final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;

        Check(String description, Reference<?> reference,
                Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
            super(description, reference, explanation);
            this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        }

        Check(Check original) {
            super(original);
            evaluatorFactory = original.evaluatorFactory;
        }

        Check(Check original, String explanation) {
            super(original, explanation);
            evaluatorFactory = original.evaluatorFactory;
        }

        @Override
        public abstract Check because(String explanation);
    }

    /** Marks checks that retain both the source type and the original observed instance. */
    public sealed interface PreservesObservation permits PreservingCondition, ComparisonCondition {}

    /** Input and expected-value constraints shared by checks used in a capture sequence. */
    public abstract static sealed class ApplicableCondition<INPUT, REFERENCE> extends Check
            permits InputCondition, ComparisonCondition {

        ApplicableCondition(String description, Reference<?> reference,
                Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
            super(description, reference, evaluatorFactory, explanation);
        }

        ApplicableCondition(Check original) {
            super(original);
        }

        ApplicableCondition(ApplicableCondition<INPUT, REFERENCE> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public abstract ApplicableCondition<INPUT, REFERENCE> because(String explanation);
    }

    /** A check whose compatibility depends on its accepted input, with no expected-value bound. */
    public abstract static sealed class InputCondition<INPUT>
            extends ApplicableCondition<INPUT, Reference<?>>
            permits Typed, PreservingCondition, SelectedCondition, MapValueCondition {

        InputCondition(String description, Reference<?> reference,
                Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
            super(description, reference, evaluatorFactory, explanation);
        }

        InputCondition(Check original) {
            super(original);
        }

        InputCondition(InputCondition<INPUT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public abstract InputCondition<INPUT> because(String explanation);
    }

    /** Immutable ordered checks; each terminal creates its own execution and captured values. */
    public abstract static sealed class Captured extends Condition
            permits CapturedSource {

        private final List<Check> stages;

        Captured(Check[] conditions) {
            super("conditions are satisfied in order", null, null);
            stages = Arrays.stream(requireNonNull(conditions, "conditions must not be null"))
                    .map(Condition::required).toList();
            if (stages.isEmpty()) throw new IllegalArgumentException("conditions must not be empty");
        }

        Captured(Captured original, String explanation) {
            super(original, explanation);
            stages = original.stages;
        }

        public final List<Check> stages() {
            return stages;
        }

        @Override
        public abstract Captured because(String explanation);
    }

    /**
     * A condition with a declared result type, such as a type check or a nested typed selection.
     *
     * @param <INPUT> accepted input type
     * @param <RESULT> successful result type
     */
    public static final class Typed<INPUT, RESULT> extends InputCondition<INPUT> {

        Typed(String description, Reference<?> reference,
                Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> evaluatorFactory) {
            super(description, reference, evaluatorFactory, null);
        }

        Typed(Condition nested, String description,
                Callable<? extends AwaitAttempt<? super INPUT, ? extends RESULT>> evaluatorFactory) {
            super(description + ": " + required(nested).description, nested.reference, evaluatorFactory, nested.explanation);
        }

        private Typed(Typed<INPUT, RESULT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public Typed<INPUT, RESULT> because(String explanation) {
            return new Typed<>(this, explanation);
        }
    }

    /**
     * Checks a value while retaining the source's concrete type at the terminal.
     * @param <INPUT> accepted input type
     */
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

    /**
     * Compares with an expected value compatible with the source type.
     * @param <EXPECTED> expected type compatible with the source
     */
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

    /**
     * A container selection whose result type is supplied by its source.
     *
     * @param <INPUT> accepted container type
     */
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

    /**
     * A map value selection whose result type is supplied by its source.
     *
     * @param <INPUT> accepted map type
     */
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

    /**
     * A sequence returning a list of original observations, with their type supplied by the source.
     *
     * @param <CHECK> check constraints used to validate the source type
     */
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
    }

    private static String nonBlank(String value, String name) {
        if (requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
