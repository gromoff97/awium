package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage.ResultStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.conditioning.runtime.ConditionRuntime;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Optional;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.conditions.ConditionSupport.preserve;
import static io.github.gromoff97.awium.conditioning.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditioning.conditions.Condition.condition;
import static java.util.Objects.requireNonNull;

public final class OptionalCondition {

    public static final SelectedCondition<Optional<?>, OptionalSource<?>> present =
            ConditionRuntime.selected("optional is present", actual ->
                    OptionalCondition.present(actual)
                    .continueIfSatisfied(Evaluation::satisfied));
    public static final Condition<Optional<?>, Void> absent = condition("optional is absent", actual -> {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isEmpty() ? satisfied(null) : unsatisfied("optional was present");
    });

    private OptionalCondition() {
        throw new AssertionError("Utility class");
    }

    public static <T> Condition<Optional<T>, T> hasValue(T expected) {
        requireNonNull(expected, "expected must not be null");
        return selected("optional value equals expected", "optional value was not equal",
                actual -> equal(actual, expected));
    }

    public static <T> Condition<Optional<T>, T> doesNotHaveValue(T unexpected) {
        requireNonNull(unexpected, "unexpected must not be null");
        return selected("optional value does not equal unexpected", "optional value was equal",
                actual -> !equal(actual, unexpected));
    }

    public static <T> Condition<Optional<T>, T> hasValue(Predicate<? super T> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("optional value matches", "optional value did not match", predicate);
    }

    public static <R> Condition<Optional<?>, R> containsInstanceOf(Class<R> type) {
        requireNonNull(type, "type must not be null");
        return condition("optional contains an instance of " + type.getTypeName(), actual -> present(actual)
                .continueIfSatisfied(value -> type.isInstance(value)
                        ? satisfied(type.cast(value)) : unsatisfied("optional value had a different type")));
    }

    public static <T, R> Condition<Optional<T>, R> hasValue(ResultStage<? super T, ? extends R> nested) {
        return ConditionRuntime.condition("optional value " + nested.description(), () -> {
            var nestedEvaluator = nested.newEvaluator();
            return actual -> present(actual).continueIfSatisfied(nestedEvaluator);
        });
    }

    public static <T> Condition<Optional<T>, T> hasValue(PreservingStage<? super T> nested) {
        return hasValue(preserve(nested));
    }

    private static <T> Condition<Optional<T>, T> selected(String description, String mismatch,
            Predicate<? super T> predicate) {
        return condition(description, actual -> present(actual)
                .continueIfSatisfied(value -> predicate.test(value)
                        ? satisfied(value) : unsatisfied(mismatch)));
    }

    private static <T> Evaluation<T> present(Optional<T> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
