package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.ConditionResult.Reference;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.CheckedCalls.call;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

/** Creates conditions and shares checks and composition across catalogs. */
final class ConditionSupport {

    private ConditionSupport() {}

    static <OBSERVED, RESULT> Condition<OBSERVED, RESULT> condition(String description, Reference<?> reference,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends RESULT>> evaluation) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>(metadata, () -> evaluation);
    }

    static <OBSERVED, RESULT> Condition<OBSERVED, RESULT> condition(String description,
            Callable<? extends CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory) {
        return new Condition<>(new ConditionMetadata(description, null, null), evaluatorFactory);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description, Reference<?> reference,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<? extends OBSERVED>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new PreservingCondition<>(metadata, () -> evaluator);
    }

    static <OBSERVED> SelectedCondition<OBSERVED> selected(String description,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<?>> evaluator) {
        return new SelectedCondition<>(new ConditionMetadata(description, null, null), () -> evaluator);
    }

    static <VALUE> Reference<VALUE> expectedReference(VALUE value) {
        return new Reference<>("Expected", value);
    }

    static <VALUE> Reference<VALUE> unexpectedReference(VALUE value) {
        return new Reference<>("Unexpected", value);
    }

    static <OBSERVED, VALUE, RESULT> Condition<OBSERVED, RESULT> compose(String prefix, Condition<?, ?> nested,
            CheckedFunction<? super OBSERVED, ? extends ConditionResult<VALUE>> extract,
            Supplier<? extends Function<? super VALUE, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory) {
        return new Condition<>(Condition.required(nested).metadata.prefixed(prefix), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> extract.apply(actual).continueIfSatisfied(evaluator);
        });
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preserving(String description, String mismatch,
            CheckedFunction<? super OBSERVED, Boolean> matches) {
        return preserving(description, actual -> matches.apply(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preservingNonNull(String subject, String description,
            String mismatch, CheckedFunction<? super OBSERVED, Boolean> matches) {
        return preservingNonNull(subject, description, mismatch, null, matches);
    }

    static <OBSERVED> PreservingCondition<OBSERVED> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, CheckedFunction<? super OBSERVED, Boolean> matches) {
        return preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.apply(actual) ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <OBSERVED> PreservingCondition<OBSERVED> sized(String subject, int bound, IntPredicate matches,
            String description, ToIntFunction<? super OBSERVED> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        return preserving(description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = call(() -> sizeOf.applyAsInt(actual));
            return matches.test(size) ? satisfied(actual) : unsatisfied(subject + " size was " + size);
        });
    }

    static <OBSERVED, ELEMENT, RESULT> Condition<OBSERVED, RESULT> single(String subject, Condition<?, ?> nested,
            Function<? super OBSERVED, ? extends Iterable<ELEMENT>> elements,
            Supplier<? extends Function<? super ELEMENT, ? extends ConditionResult<? extends RESULT>>> evaluatorFactory) {
        return new Condition<>(Condition.required(nested).metadata.prefixed(subject + " has a single matching value: "), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> actual == null ? unsatisfied(subject + " was null")
                    : evaluateSingle(call(() -> elements.apply(actual)), evaluator);
        });
    }

    private static <ELEMENT, RESULT> ConditionResult<? extends RESULT> evaluateSingle(Iterable<ELEMENT> values,
            Function<? super ELEMENT, ? extends ConditionResult<? extends RESULT>> evaluator) throws Exception {
        ConditionResult<? extends RESULT> result = unsatisfied("no value matched");
        for (var iterator = call(values::iterator); call(iterator::hasNext);) {
            ELEMENT value = call(iterator::next);
            var evaluated = evaluator.apply(value);
            if (evaluated instanceof ConditionResult.Uncontrolled<?>) return evaluated;
            if (evaluated instanceof ConditionResult.Satisfied<?>
                    && result instanceof ConditionResult.Satisfied<?>) return unsatisfied("more than one value matched");
            // Keep the first match, otherwise the last mismatch.
            if (!(result instanceof ConditionResult.Satisfied<?>)) {
                result = evaluated;
            }
        }
        return result;
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
