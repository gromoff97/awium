package io.github.gromoff97.awium;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Immutable condition metadata and evaluation. All families share explanations and metadata;
 * their input and result constraints remain checked by the terminals.
 *
 * @param <SELF> concrete condition type retained when replacing an explanation
 */
public abstract sealed class Condition<SELF extends Condition<SELF>>
        permits Condition.Typed, Condition.SourceCondition, Condition.SelectedCondition, Condition.MapValueCondition {

    private final String description;
    private final Supplier<String> explanation;
    private final Reference<?> reference;
    final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;

    Condition(String description, String explanation, Reference<?> reference,
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this(description, constantExplanation(explanation), reference, evaluatorFactory);
    }

    Condition(String description, Condition<?> metadata,
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this(description, required(metadata).explanation, metadata.reference, evaluatorFactory);
    }

    Condition(Condition<?> original) {
        this(original, required(original).explanation);
    }

    Condition(Condition<?> original, Supplier<String> explanation) {
        this(required(original).description, explanation, original.reference, original.evaluatorFactory);
    }

    private Condition(String description, Supplier<String> explanation, Reference<?> reference,
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this.description = nonBlank(description, "description");
        this.explanation = explanation;
        this.reference = reference;
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    public final String description() {
        return description;
    }

    /** Returns the explanation, evaluating a lazy supplier on each explicit read, or null when absent. */
    public final String explanation() {
        return explanation == null ? null : explanation.get();
    }

    public final Reference<?> reference() {
        return reference;
    }

    abstract SELF explained(Supplier<String> explanation);

    final SELF withExplanationFrom(Condition<?> metadata) {
        return explained(metadata.explanation);
    }

    public final SELF because(String explanation) {
        return explained(constantExplanation(nonBlank(explanation, "explanation")));
    }

    public final SELF because(String format, Object... arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return because(String.format(Locale.ROOT, format, arguments));
    }

    /**
     * Returns a copy whose explanation is evaluated when failure diagnostics need it.
     * A supplier failure or invalid text is retained as a diagnostic error without replacing the wait's outcome.
     *
     * @param explanation supplier of non-null, non-blank diagnostic text
     * @return an independent condition with the same evaluation
     */
    public final SELF because(Supplier<? extends String> explanation) {
        requireNonNull(explanation, "explanation supplier must not be null");
        return explained(() -> nonBlank(explanation.get(), "explanation"));
    }

    private static Supplier<String> constantExplanation(String explanation) {
        return explanation == null ? null : () -> explanation;
    }

    static <C extends Condition<?>> C required(C condition) {
        return requireNonNull(condition, "condition must not be null");
    }

    /**
     * A condition with a declared result type, such as a type check or a nested typed selection.
     *
     * @param <OBSERVED> accepted input type
     * @param <RESULT> successful result type
     */
    public static final class Typed<OBSERVED, RESULT> extends Condition<Typed<OBSERVED, RESULT>> {

        Typed(String description, String explanation, Reference<?> reference,
                Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
            super(description, explanation, reference, evaluatorFactory);
        }

        Typed(String description, Condition<?> metadata,
                Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
            super(description, metadata, evaluatorFactory);
        }

        private Typed(Typed<OBSERVED, RESULT> original, Supplier<String> explanation) {
            super(original, explanation);
        }

        @Override
        Typed<OBSERVED, RESULT> explained(Supplier<String> explanation) {
            return new Typed<>(this, explanation);
        }
    }

    /**
     * A check returning its original observation. Input checks use Reference&lt;?&gt;; comparisons
     * use Reference&lt;? super EXPECTED&gt;. The terminal checks both bounds against the source type.
     *
     * @param <OBSERVED> accepted input type
     * @param <REFERENCE> expected-value compatibility bound
     * @param <SELF> concrete check type retained by because
     */
    public abstract static sealed class SourceCondition<OBSERVED, REFERENCE,
            SELF extends SourceCondition<OBSERVED, REFERENCE, SELF>> extends Condition<SELF>
            permits PreservingCondition, ComparisonCondition {

        SourceCondition(Typed<OBSERVED, ?> definition) {
            super(definition);
        }

        SourceCondition(SourceCondition<OBSERVED, REFERENCE, SELF> original, Supplier<String> explanation) {
            super(original, explanation);
        }
    }

    /**
     * Checks a value while retaining the source's concrete type at the terminal.
     * @param <OBSERVED> accepted input type
     */
    public static final class PreservingCondition<OBSERVED>
            extends SourceCondition<OBSERVED, Reference<?>, PreservingCondition<OBSERVED>> {

        PreservingCondition(Typed<OBSERVED, OBSERVED> definition) {
            super(definition);
        }

        private PreservingCondition(PreservingCondition<OBSERVED> original, Supplier<String> explanation) {
            super(original, explanation);
        }

        @Override
        PreservingCondition<OBSERVED> explained(Supplier<String> explanation) {
            return new PreservingCondition<>(this, explanation);
        }
    }

    /**
     * Compares with an expected value compatible with the source type.
     * @param <EXPECTED> expected type compatible with the source
     */
    public static final class ComparisonCondition<EXPECTED>
            extends SourceCondition<Object, Reference<? super EXPECTED>, ComparisonCondition<EXPECTED>> {

        ComparisonCondition(Typed<Object, Object> definition) {
            super(definition);
        }

        private ComparisonCondition(ComparisonCondition<EXPECTED> original, Supplier<String> explanation) {
            super(original, explanation);
        }

        @Override
        ComparisonCondition<EXPECTED> explained(Supplier<String> explanation) {
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
            extends Condition<SelectedCondition<OBSERVED, CHECKED, REFERENCE>> {

        SelectedCondition(Typed<OBSERVED, ?> definition) {
            super(definition);
        }

        private SelectedCondition(SelectedCondition<OBSERVED, CHECKED, REFERENCE> original, Supplier<String> explanation) {
            super(original, explanation);
        }

        @Override
        SelectedCondition<OBSERVED, CHECKED, REFERENCE> explained(Supplier<String> explanation) {
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
            extends Condition<MapValueCondition<OBSERVED, CHECKED, REFERENCE>> {

        MapValueCondition(Typed<OBSERVED, ?> definition) {
            super(definition);
        }

        private MapValueCondition(MapValueCondition<OBSERVED, CHECKED, REFERENCE> original, Supplier<String> explanation) {
            super(original, explanation);
        }

        @Override
        MapValueCondition<OBSERVED, CHECKED, REFERENCE> explained(Supplier<String> explanation) {
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
