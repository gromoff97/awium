package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ExpectedCondition;
import io.github.gromoff97.awium.Condition.NarrowingCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Source.OptionalSource;
import java.util.Optional;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.Conditions.condition;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>, OptionalSource<?>> present =
            ConditionSupport.selected("optional is present", OptionalConditions::present);
    public static final Condition<Optional<?>, Void> absent = condition("optional is absent", actual -> {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isEmpty() ? satisfied(null) : unsatisfied("optional was present");
    });

    private OptionalConditions() {}

    public static <Value, Result> Condition<Optional<Value>, Result> hasValue(Condition<? super Value, ? extends Result> nested) {
        return compose("optional value: ", nested, OptionalConditions::present,
                () -> nested.newSession());
    }

    public static <Value> Condition<Optional<Value>, Value> hasValue(PreservingCondition<? super Value> nested) {
        return compose("optional value: ", nested, OptionalConditions::present,
                () -> nested.preservingSession());
    }

    public static <Observed, Value extends Observed> Condition<Optional<Observed>, Observed> hasValue(ExpectedCondition<Value> nested) {
        return compose("optional value: ", nested, OptionalConditions::present,
                () -> nested.preservingSession());
    }

    public static <Value, Result extends Value> Condition<Optional<Value>, Result> hasValue(NarrowingCondition<Result> nested) {
        return compose("optional value: ", nested, OptionalConditions::present,
                () -> nested.newSession());
    }

    private static <Value> ConditionResult<Value> present(Optional<Value> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
