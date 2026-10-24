package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.Reference;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

/** Creates conditions and shares checks and composition across catalogs. */
final class ConditionSupport {

    private ConditionSupport() {}

    static <OBSERVED, RESULT> Condition<OBSERVED, RESULT> condition(String description, Reference<?> reference,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends RESULT>> evaluation) {
        var condition = new Condition<OBSERVED, RESULT>(description, null, reference,
                () -> actual -> AttemptResult.of(evaluation.apply(actual)));
        requireNonNull(evaluation, "evaluation must not be null");
        return condition;
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description, Reference<?> reference,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>> evaluator) {
        var condition = new PreservingCondition<OBSERVED>(description, reference, () -> evaluator);
        requireNonNull(evaluator, "evaluation must not be null");
        return condition;
    }

    static <OBSERVED> SelectedCondition<OBSERVED> selected(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>> evaluator) {
        return new SelectedCondition<>(condition(description, null, evaluator));
    }

    static <VALUE> Reference<VALUE> expectedReference(VALUE value) {
        return new Reference<>("Expected", value);
    }

    static <VALUE> Reference<VALUE> unexpectedReference(VALUE value) {
        return new Reference<>("Unexpected", value);
    }

    static <OBSERVED, VALUE, RESULT> Condition<OBSERVED, RESULT> compose(String prefix, Condition<?, ?> condition,
            Condition<?, ?> extractionCondition,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<VALUE>> extract,
            Supplier<? extends ConditionAwaitAttempt<? super VALUE, ? extends RESULT>> evaluatorFactory) {
        return new Condition<>(prefix + condition.description(), condition.explanation(), condition.reference(), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> {
                var extracted = extract.apply(actual);
                if (extracted instanceof ConditionResult.Satisfied<VALUE> value) {
                    return evaluator.probe(value.result());
                }
                return new AttemptResult.Evaluated<>(extracted.mapSatisfied(ignored -> null),
                        extractionCondition, null);
            };
        });
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description, String mismatch,
            Predicate<? super OBSERVED> matches) {
        return preserving(description, actual -> matches.test(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preservingNonNull(String subject, String description,
            String mismatch, Predicate<? super OBSERVED> matches) {
        return preservingNonNull(subject, description, mismatch, null, matches);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, Predicate<? super OBSERVED> matches) {
        return preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <OBSERVED> PreservingCondition<OBSERVED> sized(String subject, String measure, int bound, IntPredicate matches,
            String description, ToIntFunction<? super OBSERVED> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException(measure + " must be non-negative");
        }
        return preserving(description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = sizeOf.applyAsInt(actual);
            return matches.test(size) ? satisfied(actual) : unsatisfied(subject + " " + measure + " was " + size);
        });
    }

    static <OBSERVED, ELEMENT, RESULT> Condition<OBSERVED, RESULT> single(String subject, Condition<?, ?> condition,
            Function<? super OBSERVED, ? extends Iterable<? extends ELEMENT>> elements,
            Supplier<? extends ConditionAwaitAttempt<? super ELEMENT, ? extends RESULT>> evaluatorFactory) {
        return new Condition<>(subject + " has a single matching value: " + condition.description(),
                condition.explanation(), condition.reference(), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> actual == null ? AttemptResult.of(unsatisfied(subject + " was null"))
                    : evaluateSingle(elements.apply(actual), evaluator);
        });
    }

    private static <ELEMENT, RESULT> AttemptResult<? extends RESULT> evaluateSingle(Iterable<? extends ELEMENT> values,
            ConditionAwaitAttempt<? super ELEMENT, ? extends RESULT> evaluator) {
        AttemptResult.Evaluated<? extends RESULT> result = new AttemptResult.Evaluated<>(
                unsatisfied("no value matched"), null, null);
        for (ELEMENT value : values) {
            switch (evaluator.probe(value)) {
                case AttemptResult.Uncontrolled<? extends RESULT> failure -> { return failure; }
                case AttemptResult.Evaluated<? extends RESULT> evaluated -> {
                    if (evaluated.result() instanceof ConditionResult.Satisfied<?>
                            && result.result() instanceof ConditionResult.Satisfied<?>) return AttemptResult.of(unsatisfied("more than one value matched"));
                    // Keep the first match, otherwise the last mismatch.
                    if (!(result.result() instanceof ConditionResult.Satisfied<?>)) {
                        result = evaluated;
                    }
                }
            }
        }
        return result;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED> Condition<OBSERVED, List<OBSERVED>> captured(PreservingCondition<? super OBSERVED>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null, () ->
                new SequenceExecution<OBSERVED, OBSERVED>(stages.stream()
                        .map(PreservingCondition::preservingAttempt))::step);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, ELEMENT> Condition<OBSERVED, List<ELEMENT>> captured(SelectedCondition<? super OBSERVED>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null,
                () -> new SequenceExecution<OBSERVED, ELEMENT>(stages.stream()
                        .map(SelectedCondition::selectionAttempt))::step);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, MAP_VALUE> Condition<OBSERVED, List<MAP_VALUE>> captured(MapValueCondition<? super OBSERVED>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null,
                () -> new SequenceExecution<OBSERVED, MAP_VALUE>(stages.stream()
                        .map(MapValueCondition::selectionAttempt))::step);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED> Condition<OBSERVED, List<OBSERVED>> captured(ComparisonCondition<? extends OBSERVED>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null, () ->
                new SequenceExecution<OBSERVED, OBSERVED>(stages.stream()
                        .map(stage -> stage.definition.preservingAttempt()))::step);
    }

    private static <STAGE> List<STAGE> stages(STAGE[] conditions) {
        return Arrays.stream(nonEmpty(conditions, "conditions"))
                .map(Condition::required)
                .toList();
    }

    static void validateRange(int lowerBound, int upperBound, String measure) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
    }

    static <ELEMENT> ELEMENT[] nonEmpty(ELEMENT[] values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    static <COLLECTION_TYPE extends Collection<?>> COLLECTION_TYPE nonEmpty(COLLECTION_TYPE values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    static <MAP_TYPE extends Map<?, ?>> MAP_TYPE nonEmpty(MAP_TYPE values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }
}
