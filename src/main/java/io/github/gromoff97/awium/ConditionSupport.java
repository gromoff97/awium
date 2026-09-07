package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;

import io.github.gromoff97.awium.ConditionResult.Reference;

import java.util.Collection;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static java.util.Objects.requireNonNull;

final class ConditionSupport {

    private ConditionSupport() {
        throw new AssertionError("Utility class");
    }

    static <Observed, Value, Result> Condition<Observed, Result> compose(String prefix, ConditionDefinition<?, ?> nested,
            Function<? super Observed, ? extends ConditionResult<Value>> extract,
            Supplier<? extends Function<? super Value, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return ConditionFactories.conditionFactory(ConditionDefinition.required(nested).metadata.prefixed(prefix), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> extract.apply(actual).continueIfSatisfied(evaluator);
        });
    }

    static <Observed> PreservingCondition<Observed> preserving(String description, String mismatch,
            Predicate<? super Observed> matches) {
        return preserving(description, mismatch, null, matches);
    }

    static <Observed> PreservingCondition<Observed> preserving(String description, String mismatch,
            Reference<?> reference, Predicate<? super Observed> matches) {
        return ConditionFactories.preserving(description, reference, actual -> matches.test(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <Observed> PreservingCondition<Observed> preservingNonNull(String subject, String description,
            String mismatch, Predicate<? super Observed> matches) {
        return preservingNonNull(subject, description, mismatch, null, matches);
    }

    static <Observed> PreservingCondition<Observed> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, Predicate<? super Observed> matches) {
        return ConditionFactories.preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch));
    }

    static <Observed> PreservingCondition<Observed> sized(String subject, int bound, IntPredicate matches,
            String description, ToIntFunction<? super Observed> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        return ConditionFactories.preserving(description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = sizeOf.applyAsInt(actual);
            return matches.test(size) ? satisfied(actual) : unsatisfied(subject + " size was " + size);
        });
    }

    static <Element> ConditionResult<? extends Element> selectSingle(Iterable<Element> values,
            Predicate<? super Element> matches,
            String noneMatched, String multipleMatched) throws InterruptedException {
        return evaluateSingle(values, value -> matches.test(value) ? satisfied(value) : unsatisfied(noneMatched),
                noneMatched, multipleMatched);
    }

    static <Observed, Element, Result> Condition<Observed, Result> single(String subject, ConditionDefinition<?, ?> nested,
            Function<? super Observed, ? extends Iterable<Element>> elements,
            Supplier<? extends Function<? super Element, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        return ConditionFactories.conditionFactory(ConditionDefinition.required(nested).metadata.prefixed(subject + " has a single matching value: "), () -> {
            var evaluator = evaluatorFactory.get();
            return actual -> actual == null ? unsatisfied(subject + " was null")
                    : evaluateSingle(elements.apply(actual), evaluator, "no value matched", "more than one value matched");
        });
    }

    private static <Element, Result> ConditionResult<? extends Result> evaluateSingle(Iterable<Element> values,
            Function<? super Element, ? extends ConditionResult<? extends Result>> evaluator,
            String noneMatched, String multipleMatched) throws InterruptedException {
        ConditionResult<? extends Result> result = unsatisfied(noneMatched);
        for (Element value : values) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("caller thread interrupt flag was set");
            var evaluated = requireNonNull(evaluator.apply(value), "condition returned null ConditionResult");
            if (evaluated instanceof ConditionResult.Uncontrolled<?>) return evaluated;
            if (evaluated instanceof ConditionResult.Satisfied<?>
                    && result instanceof ConditionResult.Satisfied<?>) return unsatisfied(multipleMatched);
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
