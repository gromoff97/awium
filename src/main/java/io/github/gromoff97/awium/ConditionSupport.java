package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.MapValueCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.SourceCondition;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

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
        var definition = condition(description, reference, (OBSERVED actual) ->
                requireNonNull(evaluator.apply(actual), "condition returned null ConditionResult")
                        .mapSatisfied(ignored -> actual));
        requireNonNull(evaluator, "evaluation must not be null");
        return new PreservingCondition<>(definition);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description,
            Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>>> evaluatorFactory) {
        var definition = new Condition<OBSERVED, OBSERVED>(description, null, null, () -> {
            var evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            return actual -> AttemptResult.of(
                    requireNonNull(evaluator.apply(actual), "condition returned null ConditionResult")
                            .mapSatisfied(ignored -> actual));
        });
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return new PreservingCondition<>(definition);
    }

    static <OBSERVED> SelectedCondition<OBSERVED, Object, Reference<?>> selected(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>> evaluator) {
        return new SelectedCondition<>(condition(description, null, evaluator));
    }

    static <OBSERVED, RESULT> Condition<OBSERVED, RESULT> filtered(Condition<OBSERVED, RESULT> condition,
            CheckedPredicate<? super RESULT> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return new Condition<>(condition.description() + " and value matches", condition, () -> {
            var evaluator = new ConditionAwaitAttempt<>(condition);
            return actual -> {
                var evaluation = evaluator.probe(actual);
                if (evaluation instanceof AttemptResult.Evaluated<? extends RESULT> evaluated
                        && evaluated.result() instanceof ConditionResult.Satisfied<? extends RESULT> satisfied
                        && !predicate.test(satisfied.result())) {
                    return new AttemptResult.Evaluated<>(unsatisfied("value did not match"),
                            evaluated.condition(), evaluated.progress());
                }
                return evaluation;
            };
        });
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
        return new Condition<>(prefix + condition.description(), condition, () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> {
                try {
                    return switch (extract.apply(actual)) {
                        case ConditionResult.Satisfied<VALUE> value -> evaluator.probe(value.result());
                        case ConditionResult.Unsatisfied<VALUE> mismatch -> new AttemptResult.Evaluated<>(
                                new ConditionResult.Unsatisfied<>(mismatch.mismatch(), mismatch.cause()),
                                extractionCondition, null);
                    };
                } catch (Throwable failure) {
                    return new AttemptResult.Uncontrolled<>(failure, extractionCondition, null);
                }
            };
        });
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description, String mismatch,
            CheckedPredicate<? super OBSERVED> matches) {
        return preserving(description, actual -> matches.test(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preservingNonNull(String subject, String description,
            String mismatch, CheckedPredicate<? super OBSERVED> matches) {
        return preservingNonNull(subject, description, mismatch, null, matches);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, CheckedPredicate<? super OBSERVED> matches) {
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

    static <ELEMENT, RESULT> ConditionResult<RESULT> single(String subject, Iterable<? extends ELEMENT> values,
            Function<? super ELEMENT, ? extends ConditionResult<RESULT>> selection) {
        if (values == null) return unsatisfied(subject + " was null");
        var iterator = values.iterator();
        if (!iterator.hasNext()) return unsatisfied(subject + " size was 0");
        ELEMENT value = iterator.next();
        return iterator.hasNext() ? unsatisfied(subject + " size was greater than 1")
                : selection.apply(value);
    }

    static <OBSERVED, ELEMENT, RESULT> Condition<OBSERVED, RESULT> single(String subject, Condition<?, ?> condition,
            Function<? super OBSERVED, ? extends Iterable<? extends ELEMENT>> elements,
            Supplier<? extends ConditionAwaitAttempt<? super ELEMENT, ? extends RESULT>> evaluatorFactory) {
        return new Condition<>(subject + " has a single matching value: " + condition.description(), condition, () -> {
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
                    // Keep the first match, otherwise the last mismatch.
                    if (result.result() instanceof ConditionResult.Unsatisfied<?>) {
                        result = evaluated;
                    } else if (evaluated.result() instanceof ConditionResult.Satisfied<?>) {
                        return AttemptResult.of(unsatisfied("more than one value matched"));
                    }
                }
            }
        }
        return result;
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED> Condition<OBSERVED, List<OBSERVED>> captured(
            SourceCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null, () ->
                new SequenceExecution<OBSERVED, OBSERVED>(stages.stream()
                        .map(stage -> ConditionAwaitAttempt.bind(stage.definition())))::step);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, ELEMENT> Condition<OBSERVED, List<ELEMENT>> captured(
            SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null,
                () -> new SequenceExecution<OBSERVED, ELEMENT>(stages.stream()
                        .map(stage -> ConditionAwaitAttempt.bind(stage.definition)))::step);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, MAP_VALUE> Condition<OBSERVED, List<MAP_VALUE>> captured(
            MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null,
                () -> new SequenceExecution<OBSERVED, MAP_VALUE>(stages.stream()
                        .map(stage -> ConditionAwaitAttempt.bind(stage.definition)))::step);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, RESULT> Condition<OBSERVED, List<RESULT>> captured(Condition<? super OBSERVED, RESULT>... conditions) {
        var stages = stages(conditions);
        return new Condition<>("conditions are satisfied in order", null, null, () ->
                new SequenceExecution<OBSERVED, RESULT>(stages.stream()
                        .map(ConditionAwaitAttempt::new))::step);
    }

    private static <STAGE> List<STAGE> stages(STAGE[] conditions) {
        var stages = nonEmptySnapshot(conditions, "conditions");
        stages.forEach(Condition::required);
        return stages;
    }

    static void validateRange(int lowerBound, int upperBound, String measure) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
    }

    static <ELEMENT> List<ELEMENT> snapshot(Iterable<ELEMENT> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false).toList();
    }

    static <ELEMENT> List<ELEMENT> nonEmptySnapshot(ELEMENT[] values, String name) {
        return nonEmpty(Arrays.stream(requireNonNull(values, name + " must not be null")).toList(), name);
    }

    static <ELEMENT> List<ELEMENT> nonEmptySnapshot(Iterable<ELEMENT> values, String name) {
        return nonEmpty(snapshot(values, name), name);
    }

    static <ELEMENT> List<ELEMENT> nonEmpty(List<ELEMENT> values, String name) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

}
