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

    enum Quantifier {
        ALL("all", "not all"), ANY("some", "no"), NONE("no", "some");

        private final String descriptionPrefix;
        private final String mismatchPrefix;

        Quantifier(String descriptionPrefix, String mismatchPrefix) {
            this.descriptionPrefix = descriptionPrefix;
            this.mismatchPrefix = mismatchPrefix;
        }

        String description(String elements) {
            return descriptionPrefix + " " + elements + " match";
        }

        String mismatch(String elements) {
            return mismatchPrefix + " " + elements + " matched";
        }

        boolean stops(boolean matched) {
            return switch (this) {
                case ALL -> !matched;
                case ANY, NONE -> matched;
            };
        }

        boolean satisfied(boolean stopped) {
            return switch (this) {
                case ALL, NONE -> !stopped;
                case ANY -> stopped;
            };
        }
    }

    static <OBSERVED, RESULT> Condition.Typed<OBSERVED, RESULT> condition(String description, Reference<?> reference,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends RESULT>> evaluation) {
        var condition = new Condition.Typed<OBSERVED, RESULT>(description, reference,
                () -> actual -> AttemptResult.of(evaluation.apply(actual)));
        requireNonNull(evaluation, "evaluation must not be null");
        return condition;
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description, Reference<?> reference,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>> evaluator) {
        var definition = condition(description, reference, (OBSERVED actual) ->
                requireNonNull(evaluator.apply(actual), "condition returned null ConditionResult")
                        .mapSatisfied(ignored -> actual));
        requireNonNull(evaluator, "evaluation must not be null");
        return new PreservingCondition<>(definition);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description,
            Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>>> evaluatorFactory) {
        var definition = new Condition.Typed<OBSERVED, OBSERVED>(description, null, () -> {
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

    static <VALUE> Reference<VALUE> expectedReference(VALUE value) {
        return new Reference<>("Expected", value);
    }

    static <OBSERVED, ELEMENT> PreservingCondition<OBSERVED> quantified(
            String subject, String items, Quantifier quantifier, SourceCondition<? super ELEMENT, ?> nested,
            Function<? super OBSERVED, ? extends Iterable<? extends ELEMENT>> elements) {
        return quantified(subject, items, quantifier, nested, elements,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    static <OBSERVED, ELEMENT> PreservingCondition<OBSERVED> quantified(
            String subject, String items, Quantifier quantifier, Condition.Typed<? super ELEMENT, ?> nested,
            Function<? super OBSERVED, ? extends Iterable<? extends ELEMENT>> elements) {
        return quantified(subject, items, quantifier, nested, elements,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    private static <OBSERVED, ELEMENT> PreservingCondition<OBSERVED> quantified(
            String subject, String items, Quantifier quantifier, Condition nested,
            Function<? super OBSERVED, ? extends Iterable<? extends ELEMENT>> elements,
            Supplier<? extends ConditionAwaitAttempt<? super ELEMENT, ?>> evaluatorFactory) {
        String elementsDescription = subject + " " + items;
        String mismatch = quantifier.mismatch(elementsDescription);
        return new PreservingCondition<>(new Condition.Typed<>(nested, quantifier.description(elementsDescription), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> actual == null ? AttemptResult.of(unsatisfied(subject + " was null"))
                    : evaluateQuantifier(actual, elements.apply(actual), evaluator, quantifier, mismatch);
        }));
    }

    private static <OBSERVED, ELEMENT> AttemptResult<OBSERVED> evaluateQuantifier(
            OBSERVED actual, Iterable<? extends ELEMENT> values,
            ConditionAwaitAttempt<? super ELEMENT, ?> evaluator,
            Quantifier quantifier, String mismatch) {
        AttemptResult.Evaluated<?> last = null;
        for (ELEMENT value : values) {
            switch (evaluator.probe(value)) {
                case AttemptResult.Uncontrolled<?> failure -> {
                    return new AttemptResult.Uncontrolled<>(failure.cause(), failure.condition(), failure.progress());
                }
                case AttemptResult.Evaluated<?> evaluated -> {
                    last = evaluated;
                    if (quantifier.stops(evaluated.result() instanceof ConditionResult.Satisfied<?>)) {
                        return quantifiedResult(actual, quantifier.satisfied(true), mismatch, evaluated);
                    }
                }
            }
        }
        return quantifiedResult(actual, quantifier.satisfied(false), mismatch, last);
    }

    private static <OBSERVED> AttemptResult<OBSERVED> quantifiedResult(OBSERVED actual, boolean successful,
            String mismatch, AttemptResult.Evaluated<?> evaluated) {
        if (successful) return AttemptResult.of(satisfied(actual));
        if (evaluated == null) return AttemptResult.of(unsatisfied(mismatch));
        ConditionResult<OBSERVED> failure = evaluated.result() instanceof ConditionResult.Unsatisfied<?> value
                ? unsatisfied(value.mismatch() == null ? mismatch : mismatch + ": " + value.mismatch(), value.cause())
                : unsatisfied(mismatch);
        return new AttemptResult.Evaluated<>(failure, evaluated.condition(), evaluated.progress());
    }

    static <VALUE> Reference<VALUE> unexpectedReference(VALUE value) {
        return new Reference<>("Unexpected", value);
    }

    static <OBSERVED, VALUE> Condition.Typed<OBSERVED, VALUE> compose(
            String selection, SourceCondition<?, ?> nested, Condition extractionCondition) {
        return compose(selection, nested, extractionCondition,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested));
    }

    static <OBSERVED, VALUE, RESULT> Condition.Typed<OBSERVED, RESULT> compose(
            String selection, Condition.Typed<? super VALUE, RESULT> nested, Condition extractionCondition) {
        return compose(selection, nested, extractionCondition,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    private static <OBSERVED, VALUE, RESULT> Condition.Typed<OBSERVED, RESULT> compose(String selection, Condition nested,
            Condition extractionCondition,
            Supplier<? extends ConditionAwaitAttempt<? super VALUE, ? extends RESULT>> evaluatorFactory) {
        return new Condition.Typed<>(nested, selection, () -> {
            // Bare selectors obtain their value type from the enclosing selector factory's source constraints.
            var extraction = ConditionAwaitAttempt.<OBSERVED, VALUE>bind(extractionCondition);
            var evaluator = evaluatorFactory.get();
            return actual -> switch (extraction.probe(actual)) {
                case AttemptResult.Evaluated<? extends VALUE> evaluated -> switch (evaluated.result()) {
                    case ConditionResult.Satisfied<? extends VALUE> value -> evaluator.probe(value.result());
                    case ConditionResult.Unsatisfied<?> mismatch -> new AttemptResult.Evaluated<>(
                            new ConditionResult.Unsatisfied<>(mismatch.mismatch(), mismatch.cause()),
                            extractionCondition, null);
                };
                case AttemptResult.Uncontrolled<?> failure -> new AttemptResult.Uncontrolled<>(
                        failure.cause(), extractionCondition, null);
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
            String relation, ToIntFunction<? super OBSERVED> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException(measure + " must be non-negative");
        }
        return preserving(subject + " " + measure + " " + relation, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = sizeOf.applyAsInt(actual);
            return matches.test(size) ? satisfied(actual) : unsatisfied(subject + " " + measure + " was " + size);
        });
    }

    static <ELEMENT> ConditionResult<ELEMENT> single(String subject, Iterable<? extends ELEMENT> values) {
        if (values == null) return unsatisfied(subject + " was null");
        var iterator = values.iterator();
        if (!iterator.hasNext()) return unsatisfied(subject + " size was 0");
        ELEMENT value = iterator.next();
        return iterator.hasNext() ? unsatisfied(subject + " size was greater than 1")
                : satisfied(value);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED> Condition.Typed<OBSERVED, List<OBSERVED>> captured(
            SourceCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>... conditions) {
        return captured(conditions, ConditionAwaitAttempt::bind);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, ELEMENT> Condition.Typed<OBSERVED, List<ELEMENT>> captured(
            SelectedCondition<? super OBSERVED, ? super ELEMENT, ? super Reference<? super ELEMENT>>... conditions) {
        return captured(conditions, ConditionAwaitAttempt::bind);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, MAP_VALUE> Condition.Typed<OBSERVED, List<MAP_VALUE>> captured(
            MapValueCondition<? super OBSERVED, ? super MAP_VALUE, ? super Reference<? super MAP_VALUE>>... conditions) {
        return captured(conditions, ConditionAwaitAttempt::bind);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <OBSERVED, RESULT> Condition.Typed<OBSERVED, List<RESULT>> captured(Condition.Typed<? super OBSERVED, RESULT>... conditions) {
        return captured(conditions, ConditionAwaitAttempt::new);
    }

    private static <OBSERVED, RESULT, STAGE extends Condition> Condition.Typed<OBSERVED, List<RESULT>> captured(
            STAGE[] conditions,
            Function<? super STAGE, ? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> bind) {
        var stages = Arrays.stream(requireNonNull(conditions, "conditions must not be null")).toList();
        if (stages.isEmpty()) throw new IllegalArgumentException("conditions must not be empty");
        stages.forEach(Condition::required);
        return new Condition.Typed<>("conditions are satisfied in order", null,
                () -> new SequenceExecution<>(stages.stream().map(bind))::step);
    }

    static void validateRange(int lowerBound, int upperBound, String measure) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
    }

    static <ELEMENT> List<ELEMENT> snapshot(Iterable<ELEMENT> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false).toList();
    }

}
