package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** Immutable condition metadata and evaluation, with input and result constraints checked by the terminals. */
public abstract sealed class Condition
        permits Condition.Typed, Condition.SourceCondition, Condition.SelectedCondition, Condition.MapValueCondition {

    private final String description;
    private final Reference<?> reference;
    final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;

    Condition(Condition original) {
        this(required(original).description, original.reference, original.evaluatorFactory);
    }

    private Condition(String description, Reference<?> reference,
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this.description = nonBlank(description, "description");
        this.reference = reference;
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    public final String description() {
        return description;
    }

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
            super(description, reference, evaluatorFactory);
        }

        Typed(Condition metadata, String description,
                Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
            super(description, required(metadata).reference, evaluatorFactory);
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
