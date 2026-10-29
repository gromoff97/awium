package io.github.gromoff97.awium;

import io.github.gromoff97.awium.result.AttemptStatus;
import io.github.gromoff97.awium.result.ConditionResult;
import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.function.CheckedFunction;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.internal.AttemptFactory;
import io.github.gromoff97.awium.internal.AttemptResult;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.StreamSupport.stream;

/** Creates conditions and shares checks and composition across catalogs. */
final class ConditionSupport {

    private ConditionSupport() {}

    static <INPUT, RESULT> AttemptFactory<INPUT, RESULT> bind(
            Condition.Typed<? super INPUT, ? extends RESULT> condition) {
        return bind((Condition.Check) condition);
    }

    // Closed selectors and comparisons get their result types from their factory and terminal bounds.
    static <INPUT, RESULT> AttemptFactory<INPUT, RESULT> bind(Condition.Check condition) {
        return new AttemptFactory<>(condition, condition.evaluatorFactory);
    }

    static <INPUT> AttemptFactory<INPUT, INPUT> observing(Condition.Check condition) {
        return AttemptFactory.observing(condition, condition.evaluatorFactory);
    }

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

    static <INPUT, RESULT> Condition.Typed<INPUT, RESULT> condition(String description, Reference<?> reference,
            CheckedFunction<? super INPUT, ? extends AttemptResult<? extends RESULT>> evaluation) {
        var condition = new Condition.Typed<INPUT, RESULT>(description, reference,
                () -> evaluation::apply);
        requireNonNull(evaluation, "evaluation must not be null");
        return condition;
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description,
            CheckedFunction<? super INPUT, ? extends ConditionResult> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description, Reference<?> reference,
            CheckedFunction<? super INPUT, ? extends ConditionResult> evaluator) {
        var definition = condition(description, reference, (INPUT actual) ->
                AttemptResult.of(evaluator.apply(actual), actual));
        requireNonNull(evaluator, "evaluation must not be null");
        return new PreservingCondition<>(definition);
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description,
            Callable<? extends CheckedFunction<? super INPUT, ? extends ConditionResult>> evaluatorFactory) {
        var definition = new Condition.Typed<INPUT, INPUT>(description, null, () -> {
            var evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            return actual -> AttemptResult.of(evaluator.apply(actual), actual);
        });
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return new PreservingCondition<>(definition);
    }

    static <INPUT> SelectedCondition<INPUT> selected(String description,
            CheckedFunction<? super INPUT, ? extends AttemptResult<?>> evaluator) {
        return new SelectedCondition<>(condition(description, null, evaluator));
    }

    static <VALUE> Reference<VALUE> expectedReference(VALUE value) {
        return new Reference<>("Expected", value);
    }

    static <INPUT, ELEMENT> PreservingCondition<INPUT> quantified(
            String subject, String items, Quantifier quantifier, ApplicableCondition<? super ELEMENT, ?> nested,
            Function<? super INPUT, ? extends Iterable<? extends ELEMENT>> elements) {
        String elementsDescription = subject + " " + items;
        String mismatch = quantifier.mismatch(elementsDescription);
        return new PreservingCondition<>(new Condition.Typed<>(nested, quantifier.description(elementsDescription), () -> {
            AttemptFactory<ELEMENT, ?> evaluator = bind(nested);
            return actual -> actual == null ? AttemptResult.unsatisfied(subject + " was null")
                    : evaluateQuantifier(actual, elements, evaluator, quantifier, mismatch, elementsDescription);
        }));
    }

    private static <INPUT, ELEMENT> AttemptResult<INPUT> evaluateQuantifier(
            INPUT actual, Function<? super INPUT, ? extends Iterable<? extends ELEMENT>> elements,
            AttemptFactory<? super ELEMENT, ?> evaluator,
            Quantifier quantifier, String mismatch, String elementsDescription) {
        AttemptResult<?> last = null;
        long checked = 0;
        try {
            for (ELEMENT value : elements.apply(actual)) {
                checked++;
                var evaluated = evaluator.create(value).call();
                if (evaluated instanceof AttemptResult.Uncontrolled<?> failure) {
                    return nestedFailure(elementsDescription + " at iteration index " + (checked - 1), failure);
                }
                last = evaluated;
                if (quantifier.stops(evaluated instanceof AttemptResult.Satisfied<?>)) {
                    return quantifiedResult(actual, quantifier.satisfied(true), mismatch, checked, evaluated);
                }
            }
        } catch (Throwable failure) {
            return nestedFailure(elementsDescription + " (checked " + checked + ")",
                    AttemptResult.uncontrolled(failure, null));
        }
        return quantifiedResult(actual, quantifier.satisfied(false), mismatch, checked, last);
    }

    private static <INPUT> AttemptResult<INPUT> quantifiedResult(INPUT actual, boolean successful,
            String mismatch, long checked, AttemptResult<?> evaluated) {
        return successful ? AttemptResult.satisfied(actual)
                : nestedMismatch(mismatch + " (checked " + checked + ")", evaluated);
    }

    private static <RESULT> AttemptResult.Unsatisfied<RESULT> nestedMismatch(String mismatch, AttemptResult<?> evaluated) {
        if (evaluated == null) return AttemptResult.unsatisfied(mismatch);
        var failure = evaluated.status() instanceof AttemptStatus.Unsatisfied value
                ? unsatisfied(value.mismatch() == null ? mismatch : mismatch + ": " + value.mismatch(), value.cause())
                : unsatisfied(mismatch);
        return new AttemptResult.Unsatisfied<>(failure, evaluated.condition());
    }

    private static <RESULT> AttemptResult.Uncontrolled<RESULT> nestedFailure(
            String location, AttemptResult.Uncontrolled<?> failure) {
        var status = failure.status();
        String path = status.location() == null ? location : location + " → " + status.location();
        return new AttemptResult.Uncontrolled<>(
                new AttemptStatus.Uncontrolled(status.operation(), status.cause(), path), failure.condition());
    }

    static <VALUE> Reference<VALUE> unexpectedReference(VALUE value) {
        return new Reference<>("Unexpected", value);
    }

    static <INPUT, VALUE> Condition.Typed<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, Condition.Check extractionCondition) {
        return compose(selection, nested, extractionCondition,
                () -> ConditionSupport.<VALUE, VALUE>bind(nested));
    }

    static <INPUT, VALUE, RESULT> Condition.Typed<INPUT, RESULT> compose(
            String selection, Condition.Typed<? super VALUE, RESULT> nested, Condition.Check extractionCondition) {
        return compose(selection, nested, extractionCondition,
                () -> ConditionSupport.bind(nested));
    }

    private static <INPUT, VALUE, RESULT> Condition.Typed<INPUT, RESULT> compose(String selection, Condition nested,
            Condition.Check extractionCondition,
            Supplier<? extends AttemptFactory<? super VALUE, ? extends RESULT>> evaluatorFactory) {
        return new Condition.Typed<>(nested, selection, () -> {
            // Bare selectors obtain their value type from the enclosing selector factory's source constraints.
            var extraction = ConditionSupport.<INPUT, VALUE>bind(extractionCondition);
            var evaluator = evaluatorFactory.get();
            return actual -> switch (extraction.create(actual).call()) {
                case AttemptResult.Satisfied<? extends VALUE> success -> {
                    var evaluated = evaluator.create(success.value()).call();
                    if (evaluated instanceof AttemptResult.Uncontrolled<?> failure) {
                        yield nestedFailure(selection, failure);
                    }
                    yield evaluated instanceof AttemptResult.Unsatisfied<?> && evaluated.condition() != null
                            ? nestedMismatch(selection, evaluated) : evaluated;
                }
                case AttemptResult.Unsatisfied<?> mismatch -> new AttemptResult.Unsatisfied<>(
                        mismatch.status(), extractionCondition);
                case AttemptResult.Uncontrolled<?> failure -> new AttemptResult.Uncontrolled<>(
                        failure.status(), extractionCondition);
            };
        });
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description, String mismatch,
            CheckedPredicate<? super INPUT> matches) {
        return preserving(description, actual -> matches.test(actual)
                ? satisfied() : unsatisfied(mismatch));
    }

    static <INPUT> PreservingCondition<INPUT> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, CheckedPredicate<? super INPUT> matches) {
        return preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.test(actual) ? satisfied() : unsatisfied(mismatch));
    }

    static <INPUT> PreservingCondition<INPUT> sized(String subject, String measure, int bound, IntPredicate matches,
            String relation, ToIntFunction<? super INPUT> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException(measure + " must be non-negative");
        }
        return preserving(subject + " " + measure + " " + relation, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = sizeOf.applyAsInt(actual);
            return matches.test(size) ? satisfied() : unsatisfied(subject + " " + measure + " was " + size);
        });
    }

    static <ELEMENT> AttemptResult<ELEMENT> single(String subject, Iterable<? extends ELEMENT> values) {
        if (values == null) return AttemptResult.unsatisfied(subject + " was null");
        var iterator = values.iterator();
        if (!iterator.hasNext()) return AttemptResult.unsatisfied(subject + " size was 0");
        ELEMENT value = iterator.next();
        return iterator.hasNext() ? AttemptResult.unsatisfied(subject + " size was greater than 1")
                : AttemptResult.satisfied(value);
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
