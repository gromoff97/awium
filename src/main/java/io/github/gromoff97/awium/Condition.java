package io.github.gromoff97.awium;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Typed, immutable condition. Its family preserves, checks, narrows, selects, or transforms the observed value.
 *
 * @param <OBSERVED> value supplied to the condition
 * @param <RESULT> value returned when the condition is satisfied
 */
public final class Condition<OBSERVED, RESULT> {

    private final String description;
    private final Supplier<String> explanation;
    private final Reference<?> reference;
    final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;

    Condition(String description, String explanation, Reference<?> reference,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this(description, constantExplanation(explanation), reference, evaluatorFactory);
    }

    Condition(String description, Condition<?, ?> metadata,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this(description, required(metadata).explanation, metadata.reference, evaluatorFactory);
    }

    private Condition(String description, Supplier<String> explanation, Reference<?> reference,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.description = nonBlank(description, "description");
        this.explanation = explanation;
        this.reference = reference;
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    public String description() {
        return description;
    }

    /** Returns the explanation, evaluating a lazy supplier on each explicit read, or null when absent. */
    public String explanation() {
        return explanation == null ? null : explanation.get();
    }

    public Reference<?> reference() {
        return reference;
    }

    Condition<OBSERVED, RESULT> withExplanationFrom(Condition<?, ?> metadata) {
        return new Condition<>(description, metadata.explanation, reference, evaluatorFactory);
    }

    private static Supplier<String> constantExplanation(String explanation) {
        return explanation == null ? null : () -> explanation;
    }

    static <C> C required(C condition) {
        return requireNonNull(condition, "condition must not be null");
    }

    private static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    /** Checks a satisfied result without changing its type; mismatches and failures skip the predicate. */
    public Condition<OBSERVED, RESULT> where(CheckedPredicate<? super RESULT> predicate) {
        return ConditionSupport.filtered(this, predicate);
    }

    public Condition<OBSERVED, RESULT> because(String explanation) {
        return new Condition<>(description, nonBlank(explanation, "explanation"), reference, evaluatorFactory);
    }

    public Condition<OBSERVED, RESULT> because(String format, Object... arguments) {
        return because(formattedExplanation(format, arguments));
    }

    /**
     * Returns a copy whose explanation is evaluated when failure diagnostics need it.
     * Reusing the condition may evaluate the supplier again; the supplier remains caller-owned.
     * Invalid text or a thrown failure is retained as a diagnostic error without replacing the wait's outcome.
     *
     * @param explanation supplier of non-null, non-blank diagnostic text
     * @return an independent condition with the same evaluation
     */
    public Condition<OBSERVED, RESULT> because(Supplier<? extends String> explanation) {
        requireNonNull(explanation, "explanation supplier must not be null");
        return new Condition<>(description, () -> nonBlank(explanation.get(), "explanation"), reference, evaluatorFactory);
    }

    /**
     * A check that returns the original observation. Input and expected-value bounds
     * jointly constrain the compatible source; the reference parameter carries only that constraint.
     * Input checks use {@code Reference<?>}; comparisons use {@code Reference<? super EXPECTED>}.
     * Terminals require that bound to contain {@code Reference<? super SOURCE>}, so an expected
     * subtype is allowed while a broader input check can still accept the source.
     *
     * @param <OBSERVED> input type accepted by the check
     * @param <REFERENCE> expected-value compatibility bound
     */
    public abstract static sealed class SourceCondition<OBSERVED, REFERENCE>
            permits PreservingCondition, ComparisonCondition {

        SourceCondition() {}

        abstract Condition<OBSERVED, ?> definition();

        public abstract SourceCondition<OBSERVED, REFERENCE> because(String explanation);

        public abstract SourceCondition<OBSERVED, REFERENCE> because(String format, Object... arguments);

        public abstract SourceCondition<OBSERVED, REFERENCE> because(Supplier<? extends String> explanation);
    }

    /**
     * Checks a value without replacing it. A successful wait returns the original observation
     * with its source type, even when this check accepts a supertype.
     * {@code because} returns a copy with a replacement diagnostic explanation.
     *
     * @param <OBSERVED> input type accepted by the check
     */
    public static final class PreservingCondition<OBSERVED>
            extends SourceCondition<OBSERVED, Reference<?>> {

        final Condition<OBSERVED, OBSERVED> definition;

        PreservingCondition(Condition<OBSERVED, OBSERVED> definition) {
            this.definition = required(definition);
        }

        @Override
        Condition<OBSERVED, OBSERVED> definition() {
            return definition;
        }

        /** Checks the same observation after this condition succeeds, preserving its established input type. */
        public PreservingCondition<OBSERVED> where(CheckedPredicate<? super OBSERVED> predicate) {
            return new PreservingCondition<>(ConditionSupport.filtered(definition, predicate));
        }

        public PreservingCondition<OBSERVED> because(String explanation) {
            return new PreservingCondition<>(definition.because(explanation));
        }

        public PreservingCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }

        /** @see Condition#because(Supplier) */
        public PreservingCondition<OBSERVED> because(Supplier<? extends String> explanation) {
            return new PreservingCondition<>(definition.because(explanation));
        }
    }

    /**
     * Compares with an expected type compatible with the source type. A successful wait returns
     * the original observation. {@code because} preserves the comparison's type constraint.
     *
     * @param <EXPECTED> type of expected values
     */
    public static final class ComparisonCondition<EXPECTED>
            extends SourceCondition<Object, Reference<? super EXPECTED>> {

        final Condition<Object, Object> definition;

        ComparisonCondition(Condition<Object, Object> definition) {
            this.definition = required(definition);
        }

        @Override
        Condition<Object, Object> definition() {
            return definition;
        }

        public ComparisonCondition<EXPECTED> because(String explanation) {
            return new ComparisonCondition<>(definition.because(explanation));
        }

        public ComparisonCondition<EXPECTED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }

        /** @see Condition#because(Supplier) */
        public ComparisonCondition<EXPECTED> because(Supplier<? extends String> explanation) {
            return new ComparisonCondition<>(definition.because(explanation));
        }
    }

    /**
     * Selects an element or entry from a container. The source determines the returned type,
     * including for shared constants such as {@code present} and {@code single} after {@code because}.
     *
     * @param <OBSERVED> container type accepted by the selector
     */
    public static final class SelectedCondition<OBSERVED> {

        final Condition<OBSERVED, ?> definition;

        SelectedCondition(Condition<OBSERVED, ?> definition) {
            this.definition = required(definition);
        }

        public SelectedCondition<OBSERVED> because(String explanation) {
            return new SelectedCondition<>(definition.because(explanation));
        }

        public SelectedCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }

        /** @see Condition#because(Supplier) */
        public SelectedCondition<OBSERVED> because(Supplier<? extends String> explanation) {
            return new SelectedCondition<>(definition.because(explanation));
        }
    }

    /**
     * Selects a map value whose returned type is determined by the source.
     * {@code because} preserves that result type.
     *
     * @param <OBSERVED> map type accepted by the selector
     */
    public static final class MapValueCondition<OBSERVED> {

        final Condition<OBSERVED, ?> definition;

        MapValueCondition(Condition<OBSERVED, ?> definition) {
            this.definition = required(definition);
        }

        public MapValueCondition<OBSERVED> because(String explanation) {
            return new MapValueCondition<>(definition.because(explanation));
        }

        public MapValueCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }

        /** @see Condition#because(Supplier) */
        public MapValueCondition<OBSERVED> because(Supplier<? extends String> explanation) {
            return new MapValueCondition<>(definition.because(explanation));
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
