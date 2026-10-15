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
public sealed class Condition<OBSERVED, RESULT> {

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

    static <C extends Condition<?, ?>> C required(C condition) {
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

    public static final class PreservingCondition<OBSERVED> extends Condition<OBSERVED, OBSERVED> {

        PreservingCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        @Override
        ConditionSession<OBSERVED, OBSERVED> newSession() {
            return preservingSession();
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

        @Override
        public PreservingCondition<OBSERVED> because(String explanation) {
            return new PreservingCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        @Override
        public PreservingCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class SelectedCondition<OBSERVED> extends Condition<OBSERVED, Object> {

        SelectedCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends Object>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        @Override
        public SelectedCondition<OBSERVED> because(String explanation) {
            return new SelectedCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        @Override
        public SelectedCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class MapValueCondition<OBSERVED> extends Condition<OBSERVED, Object> {

        MapValueCondition(ConditionMetadata metadata,
                Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>>> evaluatorFactory) {
            super(metadata, evaluatorFactory);
        }

        @Override
        public MapValueCondition<OBSERVED> because(String explanation) {
            return new MapValueCondition<>(metadata.because(explanation), evaluatorFactory);
        }

        @Override
        public MapValueCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }
}
