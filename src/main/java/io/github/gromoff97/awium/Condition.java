package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** Immutable condition metadata and evaluation, with input and result constraints checked by the terminals. */
public abstract sealed class Condition
        permits Condition.Typed, Condition.SourceCondition, Condition.SelectedCondition, Condition.MapValueCondition {

    private final String description;
    private final String explanation;
    private final Reference<?> reference;
    final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;

    Condition(Condition original) {
        this(required(original).description, original.reference, original.evaluatorFactory, original.explanation);
    }

    Condition(Condition original, String explanation) {
        this(required(original).description, original.reference, original.evaluatorFactory,
                nonBlank(explanation, "explanation"));
    }

    private Condition(String description, Reference<?> reference,
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory, String explanation) {
        this.description = nonBlank(description, "description");
        this.explanation = explanation;
        this.reference = reference;
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
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

    /**
     * A condition with a declared result type, such as a type check or a nested typed selection.
     *
     * @param <OBSERVED> accepted input type
     * @param <RESULT> successful result type
     */
    public static final class Typed<OBSERVED, RESULT> extends Condition {

        Typed(String description, Reference<?> reference,
                Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
            super(description, reference, evaluatorFactory, null);
        }

        Typed(Condition nested, String description,
                Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
            super(description + ": " + required(nested).description, nested.reference, evaluatorFactory, nested.explanation);
        }

        private Typed(Typed<OBSERVED, RESULT> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public Typed<OBSERVED, RESULT> because(String explanation) {
            return new Typed<>(this, explanation);
        }
    }

    /**
     * A check returning its original observation. Input checks use Reference&lt;?&gt;; comparisons
     * use Reference&lt;? super EXPECTED&gt;. The terminal checks both bounds against the source type.
     *
     * @param <OBSERVED> accepted input type
     * @param <REFERENCE> expected-value compatibility bound
     */
    public abstract static sealed class SourceCondition<OBSERVED, REFERENCE> extends Condition
            permits PreservingCondition, ComparisonCondition {

        SourceCondition(Typed<OBSERVED, ?> definition) {
            super(definition);
        }

        SourceCondition(SourceCondition<OBSERVED, REFERENCE> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public abstract SourceCondition<OBSERVED, REFERENCE> because(String explanation);
    }

    /**
     * Checks a value while retaining the source's concrete type at the terminal.
     * @param <OBSERVED> accepted input type
     */
    public static final class PreservingCondition<OBSERVED>
            extends SourceCondition<OBSERVED, Reference<?>> {

        PreservingCondition(Typed<OBSERVED, OBSERVED> definition) {
            super(definition);
        }

        private PreservingCondition(PreservingCondition<OBSERVED> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public PreservingCondition<OBSERVED> because(String explanation) {
            return new PreservingCondition<>(this, explanation);
        }
    }

    /**
     * Compares with an expected value compatible with the source type.
     * @param <EXPECTED> expected type compatible with the source
     */
    public static final class ComparisonCondition<EXPECTED>
            extends SourceCondition<Object, Reference<? super EXPECTED>> {

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
     * @param <OBSERVED> accepted container type
     * @param <CHECKED> selected value type accepted by a nested check
     * @param <REFERENCE> expected-value compatibility bound
     */
    public static final class SelectedCondition<OBSERVED, CHECKED, REFERENCE>
            extends Condition {

        SelectedCondition(Typed<OBSERVED, ?> definition) {
            super(definition);
        }

        private SelectedCondition(SelectedCondition<OBSERVED, CHECKED, REFERENCE> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public SelectedCondition<OBSERVED, CHECKED, REFERENCE> because(String explanation) {
            return new SelectedCondition<>(this, explanation);
        }
    }

    /**
     * A map value selection whose result type is supplied by its source.
     *
     * @param <OBSERVED> accepted map type
     * @param <CHECKED> value type accepted by a nested check
     * @param <REFERENCE> expected-value compatibility bound
     */
    public static final class MapValueCondition<OBSERVED, CHECKED, REFERENCE>
            extends Condition {

        MapValueCondition(Typed<OBSERVED, ?> definition) {
            super(definition);
        }

        private MapValueCondition(MapValueCondition<OBSERVED, CHECKED, REFERENCE> original, String explanation) {
            super(original, explanation);
        }

        @Override
        public MapValueCondition<OBSERVED, CHECKED, REFERENCE> because(String explanation) {
            return new MapValueCondition<>(this, explanation);
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
