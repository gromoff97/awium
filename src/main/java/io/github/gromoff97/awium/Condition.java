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

    private final String description;
    private final String explanation;
    private final Reference<?> reference;
    final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;

    Condition(String description, String explanation, Reference<?> reference,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.description = nonBlank(description, "description");
        this.explanation = explanation;
        this.reference = reference;
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    ConditionAwaitAttempt<OBSERVED, RESULT> newAttempt() {
        return new ConditionAwaitAttempt<>(this, evaluatorFactory);
    }

    final <ACTUAL extends OBSERVED> ConditionAwaitAttempt<ACTUAL, ACTUAL> preservingAttempt() {
        return new ConditionAwaitAttempt<>(this, () -> {
            var evaluator = evaluatorFactory.call();
            return actual -> {
                var evaluation = evaluator.probe(actual);
                return switch (evaluation) {
                    case AttemptResult.Evaluated<?> value -> new AttemptResult.Evaluated<>(
                            value.result().mapSatisfied(ignored -> actual), value.condition(), value.progress());
                    case AttemptResult.Uncontrolled<?> failure -> new AttemptResult.Uncontrolled<ACTUAL>(
                            failure.cause(), failure.condition(), failure.progress());
                };
            };
        });
    }

    public String description() {
        return description;
    }

    public String explanation() {
        return explanation;
    }

    public Reference<?> reference() {
        return reference;
    }

    Condition<OBSERVED, RESULT> withExplanation(String explanation) {
        return new Condition<>(description, explanation, reference, evaluatorFactory);
    }

    // Only library-created selectors use this bridge; Await binds their result to the source type.
    @SuppressWarnings("unchecked")
    final <SELECTED> ConditionAwaitAttempt<OBSERVED, SELECTED> selectionAttempt() {
        return (ConditionAwaitAttempt<OBSERVED, SELECTED>) (ConditionAwaitAttempt<?, ?>) newAttempt();
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
        return withExplanation(nonBlank(explanation, "explanation"));
    }

    public Condition<OBSERVED, RESULT> because(String format, Object... arguments) {
        return because(formattedExplanation(format, arguments));
    }

    public static final class PreservingCondition<OBSERVED> {

        final Condition<OBSERVED, OBSERVED> definition;

        PreservingCondition(String description, Reference<?> reference,
                Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory) {
            this.definition = new Condition<>(description, null, reference, () -> {
                var evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
                return actual -> AttemptResult.of(evaluator.apply(actual));
            });
            requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        }

        private PreservingCondition(Condition<OBSERVED, OBSERVED> definition) {
            this.definition = definition;
        }

        final <ACTUAL extends OBSERVED> ConditionAwaitAttempt<ACTUAL, ACTUAL> preservingAttempt() {
            return definition.preservingAttempt();
        }

        public PreservingCondition<OBSERVED> because(String explanation) {
            return new PreservingCondition<>(definition.because(explanation));
        }

        public PreservingCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
        }
    }

    public static final class ComparisonCondition<EXPECTED> {

        final Condition<Object, Object> definition;

        ComparisonCondition(Condition<Object, Object> definition) {
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

        <SELECTED> ConditionAwaitAttempt<OBSERVED, SELECTED> selectionAttempt() {
            return definition.selectionAttempt();
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

        <VALUE> ConditionAwaitAttempt<OBSERVED, VALUE> selectionAttempt() {
            return definition.selectionAttempt();
        }

        public MapValueCondition<OBSERVED> because(String explanation) {
            return new MapValueCondition<>(definition.because(explanation));
        }

        public MapValueCondition<OBSERVED> because(String format, Object... arguments) {
            return because(formattedExplanation(format, arguments));
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
