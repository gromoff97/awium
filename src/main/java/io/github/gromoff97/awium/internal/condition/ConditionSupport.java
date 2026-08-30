package io.github.gromoff97.awium.internal.condition;

import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingStage;
import io.github.gromoff97.awium.results.AwaitAttempt.Reference;

import java.util.Collection;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.assessedCondition;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.description;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.explanation;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.preservingEvaluator;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.reference;
import static java.util.Objects.requireNonNull;

public final class ConditionSupport {

    private ConditionSupport() {
        throw new AssertionError("Utility class");
    }

    public static <Observed> Condition<Observed, Observed> preserve(PreservingStage<? super Observed> nested) {
        return assessedCondition(description(nested), explanation(nested), reference(nested), () -> preservingEvaluator(nested));
    }

    public static <Observed> PreservingCondition<Observed> preserving(String description, String mismatch,
            Predicate<? super Observed> matches) {
        return preserving(description, mismatch, null, matches);
    }

    public static <Observed> PreservingCondition<Observed> preserving(String description, String mismatch,
            Reference<?> reference, Predicate<? super Observed> matches) {
        return ConditionRuntime.preserving(description, reference, actual -> matches.test(actual)
                ? satisfied(actual) : unsatisfied(mismatch));
    }

    public static <Observed> PreservingCondition<Observed> preservingNonNull(String subject, String description,
            String mismatch, Predicate<? super Observed> matches) {
        return preservingNonNull(subject, description, mismatch, null, matches);
    }

    public static <Observed> PreservingCondition<Observed> preservingNonNull(String subject, String description,
            String mismatch, Reference<?> reference, Predicate<? super Observed> matches) {
        return ConditionRuntime.preserving(description, reference, actual -> actual == null
                ? unsatisfied(subject + " was null")
                : matches.test(actual) ? satisfied(actual) : unsatisfied(mismatch));
    }

    public static <Observed> PreservingCondition<Observed> sized(String subject, int bound, IntPredicate matches,
            String description, ToIntFunction<? super Observed> sizeOf) {
        if (bound < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        return ConditionRuntime.preserving(description, actual -> {
            if (actual == null) {
                return unsatisfied(subject + " was null");
            }
            int size = sizeOf.applyAsInt(actual);
            return matches.test(size) ? satisfied(actual) : unsatisfied(subject + " size was " + size);
        });
    }

    public static <Element> ConditionEvaluation<Element> selectSingle(Iterable<Element> values,
            Predicate<? super Element> matches,
            String noneMatched, String multipleMatched) {
        Element selected = null;
        boolean found = false;
        for (Element value : values) {
            if (!matches.test(value)) {
                continue;
            }
            if (found) {
                return unsatisfied(multipleMatched);
            }
            selected = value;
            found = true;
        }
        return found ? satisfied(selected) : unsatisfied(noneMatched);
    }

    public static void validateRange(int lowerBound, int upperBound, String measure) {
        if (lowerBound < 0 || upperBound < lowerBound) {
            throw new IllegalArgumentException(measure + " range must be non-negative and ordered");
        }
    }

    public static <Element> Element[] nonEmpty(Element[] values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    public static <CollectionType extends Collection<?>> CollectionType nonEmpty(CollectionType values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }

    public static <MapType extends Map<?, ?>> MapType nonEmpty(MapType values, String name) {
        requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return values;
    }
}
