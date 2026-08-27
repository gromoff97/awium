package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.nonEmpty;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preserve;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preserving;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.matchesAny;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.condition;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public final class ObjectCondition {

    public static final Condition<Object, Void> isNull = condition("value is null",
            actual -> actual == null ? satisfied(null) : unsatisfied("value was not null"));
    public static final PreservingCondition<Object> isNotNull = preserving("value is not null",
            "value was null", actual -> actual != null);

    private ObjectCondition() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return preserving("value equals expected", "value was not equal",
                actual -> equal(actual, expected));
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return preserving("value does not equal unexpected", "value was equal",
                actual -> !equal(actual, unexpected));
    }

    public static PreservingCondition<Object> sameAs(Object expected) {
        return preserving("value is the same instance", "value was a different instance",
                actual -> actual == expected);
    }

    public static PreservingCondition<Object> notSameAs(Object unexpected) {
        return preserving("value is not the same instance", "value was the same instance",
                actual -> actual != unexpected);
    }

    public static <R> Condition<Object, R> instanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("value is an instance of " + type.getTypeName(), actual ->
                actual != null && type.isInstance(actual)
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not an instance of " + type.getTypeName()));
    }

    public static <R> Condition<Object, R> exactInstanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("value is exactly an instance of " + type.getTypeName(), actual ->
                actual != null && actual.getClass() == type
                        ? satisfied(type.cast(actual))
                        : unsatisfied("value was not exactly an instance of " + type.getTypeName()));
    }

    public static PreservingCondition<Object> in(Object... expected) {
        List<Object> values = asList(nonEmpty(expected, "expected values"));
        return preserving("value is in the expected values", "value was not in the expected values",
                actual -> matchesAny(values, candidate -> equal(actual, candidate)));
    }

    public static PreservingCondition<Object> notIn(Object... unexpected) {
        List<Object> values = asList(nonEmpty(unexpected, "unexpected values"));
        return preserving("value is not in the unexpected values", "value was in the unexpected values",
                actual -> !matchesAny(values, candidate -> equal(actual, candidate)));
    }

    public static <S> PreservingCondition<S> matches(Predicate<? super S> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return preserving("value matches", "value did not match", predicate);
    }

    public static <S, T, R> Condition<S, R> extracting(Function<? super S, ? extends T> extractor,
            ResultStage<? super T, ? extends R> nested) {
        requireNonNull(extractor, "extractor must not be null");
        return ConditionRuntime.condition("extracted " + nested.description(), () -> {
            var nestedEvaluator = nested.newEvaluator();
            return actual -> nestedEvaluator.apply(extractor.apply(actual));
        });
    }

    public static <S, T> Condition<S, T> extracting(Function<? super S, ? extends T> extractor,
            PreservingStage<? super T> nested) {
        return extracting(extractor, preserve(nested));
    }

}
