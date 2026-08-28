package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.fluent.Condition.PreservingCondition;
import io.github.gromoff97.awium.fluent.Condition.PreservingStage;
import io.github.gromoff97.awium.fluent.Condition.ExpectedStage;
import io.github.gromoff97.awium.fluent.Condition.NarrowingStage;
import io.github.gromoff97.awium.fluent.ConditionStage.ResultStage;
import io.github.gromoff97.awium.fluent.Condition.SelectedCondition;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Optional;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.engine.ConditionAssessment.continueIfSatisfied;
import static io.github.gromoff97.awium.fluent.ConditionSupport.preserve;
import static io.github.gromoff97.awium.fluent.ConditionRuntime.assessedCondition;
import static io.github.gromoff97.awium.fluent.ValueMatching.equal;
import static io.github.gromoff97.awium.fluent.Conditions.condition;
import static java.util.Objects.requireNonNull;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>, OptionalSource<?>> present =
            ConditionRuntime.selected("optional is present", OptionalConditions::present);
    public static final Condition<Optional<?>, Void> absent = condition("optional is absent", actual -> {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isEmpty() ? satisfied(null) : unsatisfied("optional was present");
    });

    private OptionalConditions() {
        throw new AssertionError("Utility class");
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(Value expected) {
        requireNonNull(expected, "expected must not be null");
        return selected("optional value equals expected", "optional value was not equal",
                actual -> equal(actual, expected));
    }

    public static <Value> Condition<Optional<Value>, Value> doesNotHaveValue(Value unexpected) {
        requireNonNull(unexpected, "unexpected must not be null");
        return selected("optional value does not equal unexpected", "optional value was equal",
                actual -> !equal(actual, unexpected));
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(Predicate<? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("optional value matches", "optional value did not match", predicate);
    }

    public static <Value, Result extends Value> Condition<Optional<Value>, Result> containsInstanceOf(Class<Result> type) {
        requireNonNull(type, "type must not be null");
        return condition("optional contains an instance of " + type.getTypeName(), actual -> present(actual)
                .continueIfSatisfied(value -> type.isInstance(value)
                        ? satisfied(type.cast(value)) : unsatisfied("optional value had a different type")));
    }

    public static <Value, Result> Condition<Optional<Value>, Result> hasValue(ResultStage<? super Value, ? extends Result> nested) {
        return assessedCondition("optional value " + ConditionRuntime.description(nested), () -> {
            var nestedEvaluator = ConditionRuntime.<Value, Result>evaluator(nested);
            return actual -> continueIfSatisfied(present(actual), nestedEvaluator);
        });
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(PreservingStage<? super Value> nested) {
        return hasValue(preserve(nested));
    }

    public static <Observed, Value extends Observed> Condition<Optional<Observed>, Observed> hasValue(ExpectedStage<Value> nested) {
        return assessedCondition("optional value " + ConditionRuntime.description(nested), () -> {
            var nestedEvaluator = ConditionRuntime.<Observed>expectedEvaluator(nested);
            return actual -> continueIfSatisfied(present(actual), nestedEvaluator);
        });
    }

    public static <Value, Result extends Value> Condition<Optional<Value>, Result> hasValue(NarrowingStage<Result> nested) {
        return assessedCondition("optional value " + ConditionRuntime.description(nested), () -> {
            var nestedEvaluator = ConditionRuntime.<Value, Result>narrowingEvaluator(nested);
            return actual -> continueIfSatisfied(present(actual), nestedEvaluator);
        });
    }

    private static <Value> Condition<Optional<Value>, Value> selected(String description, String mismatch,
            Predicate<? super Value> predicate) {
        return condition(description, actual -> present(actual)
                .continueIfSatisfied(value -> predicate.test(value)
                        ? satisfied(value) : unsatisfied(mismatch)));
    }

    private static <Value> ConditionEvaluation<Value> present(Optional<Value> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
