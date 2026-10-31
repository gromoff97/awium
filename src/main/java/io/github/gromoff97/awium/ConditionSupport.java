package io.github.gromoff97.awium;

import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.internal.AwaitAttempt;
import io.github.gromoff97.awium.internal.ConditionExecution;
import io.github.gromoff97.awium.internal.ProbeFailure;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.Condition.ApplicableCondition;
import static io.github.gromoff97.awium.Condition.Captured;
import static io.github.gromoff97.awium.Condition.Check;
import static io.github.gromoff97.awium.Condition.PreservingCondition;
import static io.github.gromoff97.awium.Condition.Reference;
import static io.github.gromoff97.awium.Condition.SelectedCondition;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.internal.ProbeFailure.locatedFailure;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.Unsatisfied;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;
import static java.util.stream.StreamSupport.stream;

final class ConditionSupport {

    private ConditionSupport() {}

    static <INPUT, RESULT> ConditionExecution<INPUT, RESULT> bind(
            Typed<? super INPUT, ? extends RESULT> condition) {
        return bind((Check) condition);
    }

    // Factories and terminal bounds establish input and result types before binding.
    static <INPUT, RESULT> ConditionExecution<INPUT, RESULT> bind(Check condition) {
        return new ConditionExecution<>(required(condition).evaluatorFactory);
    }

    static <INPUT, RESULT> SequenceExecution<INPUT, RESULT> sequence(Check condition) {
        return new SequenceExecution<>(List.of(bind(condition)));
    }

    static <INPUT> SequenceExecution<INPUT, INPUT> sequence(Captured<?> condition) {
        return new SequenceExecution<>(required(condition).stages().stream()
                .map(stage -> ConditionExecution.<INPUT>observing(stage.evaluatorFactory)).toList());
    }

    enum Quantifier {
        ALL("all", "not all"), ANY("some", "no"), NONE("no", "some");

        private final String descriptionPrefix;
        private final String mismatchPrefix;

        Quantifier(String descriptionPrefix, String mismatchPrefix) {
            this.descriptionPrefix = descriptionPrefix;
            this.mismatchPrefix = mismatchPrefix;
        }

        private String description(String elements) {
            return descriptionPrefix + " " + elements + " match";
        }

        private String mismatch(String elements) {
            return mismatchPrefix + " " + elements + " matched";
        }

        private boolean stops(boolean matched) {
            return switch (this) {
                case ALL -> !matched;
                case ANY, NONE -> matched;
            };
        }

        private boolean isSatisfied(boolean stopped) {
            return switch (this) {
                case ALL, NONE -> !stopped;
                case ANY -> stopped;
            };
        }
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description,
            AwaitAttempt<? super INPUT, ? extends INPUT> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description, Reference<?> reference,
            AwaitAttempt<? super INPUT, ? extends INPUT> evaluator) {
        return new PreservingCondition<>(description, reference, evaluator);
    }

    static <INPUT> SelectedCondition<INPUT> selected(String description,
            AwaitAttempt<? super INPUT, ?> evaluator) {
        return new SelectedCondition<>(new Typed<>(description, null, evaluator));
    }

    static <VALUE> Reference<VALUE> expectedReference(VALUE value) {
        return new Reference<>("Expected", value);
    }

    static <INPUT, ELEMENT> PreservingCondition<INPUT> quantified(
            String subject, String items, Quantifier quantifier, ApplicableCondition<? super ELEMENT, ?> nested,
            Function<? super INPUT, ? extends Iterable<? extends ELEMENT>> elements) {
        String elementsDescription = subject + " " + items;
        String mismatch = quantifier.mismatch(elementsDescription);
        required(nested);
        return new PreservingCondition<>(nested, quantifier.description(elementsDescription), () -> {
            ConditionExecution<ELEMENT, ?> evaluator = bind(nested);
            return actual -> actual == null ? unsatisfied(subject + " was null")
                    : evaluateQuantifier(actual, elements, evaluator, quantifier, mismatch, elementsDescription);
        });
    }

    private static <INPUT, ELEMENT> ConditionResult<INPUT> evaluateQuantifier(
            INPUT actual, Function<? super INPUT, ? extends Iterable<? extends ELEMENT>> elements,
            ConditionExecution<? super ELEMENT, ?> evaluator,
            Quantifier quantifier, String mismatch, String elementsDescription) throws Throwable {
        ConditionResult<?> last = null;
        long checked = 0;
        try {
            for (ELEMENT value : elements.apply(actual)) {
                checked++;
                var evaluated = evaluator.probeAt(value, elementsDescription + "[" + (checked - 1) + "]");
                last = evaluated;
                if (quantifier.stops(evaluated instanceof Satisfied<?>)) {
                    return quantifiedResult(actual, quantifier.isSatisfied(true), mismatch, checked, evaluated);
                }
            }
        } catch (ProbeFailure failure) {
            throw failure;
        } catch (Throwable failure) {
            throw locatedFailure(elementsDescription + " (checked " + checked + ")", failure);
        }
        return quantifiedResult(actual, quantifier.isSatisfied(false), mismatch, checked, last);
    }

    private static <INPUT> ConditionResult<INPUT> quantifiedResult(INPUT actual, boolean successful,
            String mismatch, long checked, ConditionResult<?> evaluated) {
        return successful ? satisfied(actual)
                : nestedMismatch(mismatch + " (checked " + checked + ")", evaluated);
    }

    private static <RESULT> Unsatisfied<RESULT> nestedMismatch(String mismatch, ConditionResult<?> evaluated) {
        if (!(evaluated instanceof Unsatisfied<?> failure)) return unsatisfied(mismatch);
        return unsatisfied(failure.mismatch() == null ? mismatch : mismatch + ": " + failure.mismatch(), failure.cause());
    }

    static <VALUE> Reference<VALUE> unexpectedReference(VALUE value) {
        return new Reference<>("Unexpected", value);
    }

    static <INPUT, VALUE> Typed<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, Check extractionCondition) {
        return compose(selection, nested, extractionCondition,
                () -> ConditionSupport.<VALUE, VALUE>bind(nested));
    }

    static <INPUT, VALUE, RESULT> Typed<INPUT, RESULT> compose(
            String selection, Typed<? super VALUE, RESULT> nested, Check extractionCondition) {
        return compose(selection, nested, extractionCondition,
                () -> bind(nested));
    }

    private static <INPUT, VALUE, RESULT> Typed<INPUT, RESULT> compose(String selection, Condition nested,
            Check extractionCondition,
            Supplier<? extends ConditionExecution<? super VALUE, ? extends RESULT>> evaluatorFactory) {
        required(nested);
        var references = concat(extractionCondition.references().stream(), nested.references().stream()).toList();
        return new Typed<>(nested, selection, references, () -> {
            // Bare selectors obtain their value type from the enclosing selector factory's source constraints.
            var extraction = ConditionSupport.<INPUT, VALUE>bind(extractionCondition);
            var evaluator = evaluatorFactory.get();
            return actual -> switch (extraction.probe(actual)) {
                case Satisfied<? extends VALUE> success -> {
                    var evaluated = evaluator.probeAt(success.value(), selection);
                    yield evaluated instanceof Unsatisfied<?>
                            ? nestedMismatch(selection, evaluated) : evaluated;
                }
                case Unsatisfied<?> mismatch -> unsatisfied(mismatch.mismatch(), mismatch.cause());
            };
        });
    }

    static <INPUT> PreservingCondition<INPUT> preserving(String description, String mismatch,
            CheckedPredicate<? super INPUT> matches) {
        return preserving(description, actual -> matches.test(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <INPUT> PreservingCondition<INPUT> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, CheckedPredicate<? super INPUT> matches) {
        return preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch));
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

    static void validateRange(int lowerBound, int upperBound, String measure) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
    }

    static <ELEMENT> List<ELEMENT> snapshot(Iterable<ELEMENT> values, String name) {
        return stream(requireNonNull(values, name + " must not be null").spliterator(), false).toList();
    }

}
