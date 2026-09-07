package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;

import io.github.gromoff97.awium.Source.OptionalSource;
import io.github.gromoff97.awium.ConditionResult.Reference;

import java.util.Optional;
import java.util.function.Predicate;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.ConditionFactories.expectedReference;
import static io.github.gromoff97.awium.ConditionFactories.unexpectedReference;
import static io.github.gromoff97.awium.ValueMatching.equal;
import static io.github.gromoff97.awium.Conditions.condition;
import static java.util.Objects.requireNonNull;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>, OptionalSource<?>> present =
            ConditionFactories.selected("optional is present", OptionalConditions::present);
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
                () -> nested.newSession());
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(PreservingCondition<? super Value> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> nested.preservingSession());
    }

    public static <Observed, Value extends Observed> Condition<Optional<Observed>, Observed> hasValue(ExpectedCondition<Value> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> nested.preservingSession());
    }

    public static <Value, Result extends Value> Condition<Optional<Value>, Result> hasValue(NarrowingCondition<Result> nested) {
        return compose("optional value ", nested, OptionalConditions::present,
                () -> nested.newSession());
    }

    private static <Value> Condition<Optional<Value>, Value> selected(String description, String mismatch,
            Predicate<? super Value> predicate) {
        return selected(description, mismatch, null, predicate);
    }

    private static <Value> Condition<Optional<Value>, Value> selected(String description, String mismatch,
            Reference<?> reference,
            Predicate<? super Value> predicate) {
        return ConditionFactories.condition(description, reference, actual -> present(actual)
                .continueIfSatisfied(value -> predicate.test(value)
                        ? satisfied(value) : unsatisfied(mismatch)));
    }

    private static <Value> ConditionResult<Value> present(Optional<Value> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
