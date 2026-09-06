package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.condition.Condition;
import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.internal.condition.ConditionRuntime;
import io.github.gromoff97.awium.condition.Condition.ExpectedCondition;
import io.github.gromoff97.awium.condition.Condition.NarrowingCondition;
import io.github.gromoff97.awium.condition.Condition.PreservingCondition;
import io.github.gromoff97.awium.condition.Condition.SelectedCondition;

import io.github.gromoff97.awium.sources.Source.OptionalSource;
import io.github.gromoff97.awium.results.AwaitAttempt.Reference;

import java.util.Optional;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.conditions.ConditionSupport.compose;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.expectedReference;
import static io.github.gromoff97.awium.internal.condition.ConditionRuntime.unexpectedReference;
import static io.github.gromoff97.awium.conditions.ValueMatching.equal;
import static io.github.gromoff97.awium.conditions.Conditions.condition;
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
                expectedReference(expected), actual -> equal(actual, expected));
    }

    public static <Value> Condition<Optional<Value>, Value> doesNotHaveValue(Value unexpected) {
        requireNonNull(unexpected, "unexpected must not be null");
        return selected("optional value does not equal unexpected", "optional value was equal",
                unexpectedReference(unexpected), actual -> !equal(actual, unexpected));
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(Predicate<? super Value> predicate) {
        requireNonNull(predicate, "predicate must not be null");
        return selected("optional value matches", "optional value did not match", predicate);
    }

    public static <Value, Result> Condition<Optional<Value>, Result> hasValue(Condition<? super Value, ? extends Result> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> ConditionRuntime.<Value, Result>evaluator(nested));
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(PreservingCondition<? super Value> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> ConditionRuntime.<Value>preservingEvaluator(nested));
    }

    public static <Observed, Value extends Observed> Condition<Optional<Observed>, Observed> hasValue(ExpectedCondition<Value> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> ConditionRuntime.<Observed>preservingEvaluator(nested));
    }

    public static <Value, Result extends Value> Condition<Optional<Value>, Result> hasValue(NarrowingCondition<Result> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> ConditionRuntime.<Value, Result>evaluator(nested));
    }

    private static <Value> Condition<Optional<Value>, Value> selected(String description, String mismatch,
            Predicate<? super Value> predicate) {
        return selected(description, mismatch, null, predicate);
    }

    private static <Value> Condition<Optional<Value>, Value> selected(String description, String mismatch,
            Reference<?> reference,
            Predicate<? super Value> predicate) {
        return ConditionRuntime.condition(description, reference, actual -> present(actual)
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
