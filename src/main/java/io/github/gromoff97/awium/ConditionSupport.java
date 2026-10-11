package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
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

    static <Observed, Result> Condition<Observed, Result> condition(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>> evaluation) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluation, "evaluation must not be null");
        return new Condition<>(metadata, () -> evaluation);
    }

    static <Observed, Result> Condition<Observed, Result> condition(String description,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return new Condition<>(new ConditionMetadata(description, null, null), evaluatorFactory);
    }

    static <Observed> PreservingCondition<Observed> preserving(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>> evaluator) {
        return preserving(description, null, evaluator);
    }

    static <Observed> PreservingCondition<Observed> preserving(String description, Reference<?> reference,
            CheckedFunction<? super Observed, ? extends ConditionResult<? extends Observed>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new PreservingCondition<>(metadata, () -> evaluator);
    }

    static <Expected> ExpectedCondition<Expected> expected(String description, Reference<?> reference,
            CheckedFunction<Object, ConditionResult<Object>> evaluator) {
        var metadata = new ConditionMetadata(description, null, reference);
        requireNonNull(evaluator, "evaluation must not be null");
        return new ExpectedCondition<>(metadata, () -> evaluator);
    }

    static <Result> NarrowingCondition<Result> narrowing(String description, CheckedFunction<Object, ConditionResult<Result>> evaluator) {
        var metadata = new ConditionMetadata(description, null, null);
        requireNonNull(evaluator, "evaluation must not be null");
        return new NarrowingCondition<>(metadata, () -> evaluator);
    }

    static <Observed, Family> SelectedCondition<Observed, Family> selected(String description,
            CheckedFunction<? super Observed, ? extends ConditionResult<?>> evaluator) {
        var metadata = new ConditionMetadata(description, null, null);
        requireNonNull(evaluator, "evaluation must not be null");
        return new SelectedCondition<>(metadata, () -> evaluator);
    }

    static <Value> Reference<Value> expectedReference(Value value) {
        return new Reference<>("Expected", value);
    }

    static <Value> Reference<Value> unexpectedReference(Value value) {
        return new Reference<>("Unexpected", value);
    }

    static <Observed, Value, Result> Condition<Observed, Result> compose(String prefix, ConditionDefinition<?, ?> nested,
            CheckedFunction<? super Observed, ? extends ConditionResult<Value>> extract,
            Supplier<? extends Function<? super Value, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return new Condition<>(ConditionDefinition.required(nested).metadata.prefixed(prefix), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> extract.apply(actual).continueIfSatisfied(evaluator);
        });
    }

    static <Observed> PreservingCondition<Observed> preserving(String description, String mismatch,
            CheckedFunction<? super Observed, Boolean> matches) {
        return preserving(description, actual -> matches.apply(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <Observed> PreservingCondition<Observed> preservingNonNull(String subject, String description,
            String mismatch, CheckedFunction<? super Observed, Boolean> matches) {
        return preservingNonNull(subject, description, mismatch, null, matches);
    }

    static <Observed> PreservingCondition<Observed> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, CheckedFunction<? super Observed, Boolean> matches) {
        return preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.apply(actual) ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <Observed> PreservingCondition<Observed> sized(String subject, int bound, IntPredicate matches,
            String description, ToIntFunction<? super Observed> sizeOf) {
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

    static <Observed, Element, Result> Condition<Observed, Result> single(String subject, ConditionDefinition<?, ?> nested,
            Function<? super Observed, ? extends Iterable<Element>> elements,
            Supplier<? extends Function<? super Element, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return new Condition<>(ConditionDefinition.required(nested).metadata.prefixed(subject + " has a single matching value: "), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> actual == null ? unsatisfied(subject + " was null")
                    : evaluateSingle(call(() -> elements.apply(actual)), evaluator);
        });
    }

    private static <Element, Result> ConditionResult<? extends Result> evaluateSingle(Iterable<Element> values,
            Function<? super Element, ? extends ConditionResult<? extends Result>> evaluator) throws Exception {
        ConditionResult<? extends Result> result = unsatisfied("no value matched");
        for (var iterator = call(values::iterator); call(iterator::hasNext);) {
            Element value = call(iterator::next);
            var evaluated = requireNonNull(evaluator.apply(value), "condition returned null ConditionResult");
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

    static <Element> Element[] nonEmpty(Element[] values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    static <CollectionType extends Collection<?>> CollectionType nonEmpty(CollectionType values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    static <MapType extends Map<?, ?>> MapType nonEmpty(MapType values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }
}
