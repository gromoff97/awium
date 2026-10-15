package io.github.gromoff97.awium;

import java.util.Locale;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/**
 * Typed, immutable condition. Its family preserves, checks, narrows, selects, or transforms the observed value.
 *
 * @param <OBSERVED> value supplied to the condition
 * @param <RESULT> value returned when the condition is satisfied
 */
public final class Condition<OBSERVED, RESULT> {

    final ConditionMetadata metadata;
    final Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory;

    Condition(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory) {
        this.metadata = requireNonNull(metadata, "metadata must not be null");
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    ConditionSession<OBSERVED, RESULT> newSession() {
        return new ConditionSession<>(metadata, evaluatorFactory);
    }

    // Only library-created selectors use this bridge; Await binds their result to the source type.
    @SuppressWarnings("unchecked")
    final <SELECTED> ConditionSession<OBSERVED, SELECTED> selectionSession() {
        return (ConditionSession<OBSERVED, SELECTED>) (ConditionSession<?, ?>) newSession();
    }

    static <C> C required(C condition) {
        return requireNonNull(condition, "condition must not be null");
    }

    static String formattedExplanation(String format, Object[] arguments) {
        requireNonNull(format, "format must not be null");
        requireNonNull(arguments, "arguments must not be null");
        return String.format(Locale.ROOT, format, arguments);
    }

    public Condition<OBSERVED, RESULT> because(String explanation) {
        return new Condition<>(metadata.because(explanation), evaluatorFactory);
    }

    public Condition<OBSERVED, RESULT> because(String format, Object... arguments) {
        return because(formattedExplanation(format, arguments));
    }

    public static final class PreservingCondition<OBSERVED> {

        final ConditionMetadata metadata;
        final Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory;

        PreservingCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory) {
            this.metadata = requireNonNull(metadata, "metadata must not be null");
            this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        }

        final <ACTUAL extends OBSERVED> ConditionSession<ACTUAL, ACTUAL> preservingSession() {
            return new ConditionSession<>(metadata, () -> {
                var evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
                return actual -> {
                    var result = evaluator.apply(actual);
                    return result == null ? null : result.mapSatisfied(ignored -> actual);
                };
            });
        }

        public PreservingCondition<OBSERVED> because(String explanation) {
            return new PreservingCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        public PreservingCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class ComparisonCondition<EXPECTED> {

        final PreservingCondition<Object> definition;

        ComparisonCondition(PreservingCondition<Object> definition) {
            this.definition = required(definition);
        }

        public ComparisonCondition<EXPECTED> because(String explanation) {
            return new ComparisonCondition<>(definition.because(explanation));
        }

        public ComparisonCondition<EXPECTED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class SelectedCondition<OBSERVED> {

        final Condition<OBSERVED, ?> definition;

        SelectedCondition(Condition<OBSERVED, ?> definition) {
            this.definition = required(definition);
        }

        <SELECTED> ConditionSession<OBSERVED, SELECTED> selectionSession() {
            return definition.selectionSession();
        }

        public SelectedCondition<OBSERVED> because(String explanation) {
            return new SelectedCondition<>(definition.because(explanation));
        }

        public SelectedCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class MapValueCondition<OBSERVED> {

        final Condition<OBSERVED, ?> definition;

        MapValueCondition(Condition<OBSERVED, ?> definition) {
            this.definition = required(definition);
        }

        <VALUE> ConditionSession<OBSERVED, VALUE> selectionSession() {
            return definition.selectionSession();
        }

        public MapValueCondition<OBSERVED> because(String explanation) {
            return new MapValueCondition<>(definition.because(explanation));
        }

        public MapValueCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }
}
