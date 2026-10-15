package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.Optional;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.Conditions.condition;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>> present =
            ConditionSupport.selected("optional is present", OptionalConditions::present);
    public static final Condition<Optional<?>, Void> absent = condition("optional is absent", actual -> {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isEmpty() ? satisfied(null) : unsatisfied("optional was present");
    });

    private OptionalConditions() {}

    public static <VALUE, RESULT> Condition<Optional<VALUE>, RESULT> hasValue(Condition<? super VALUE, ? extends RESULT> nested) {
        return compose("optional value: ", nested, OptionalConditions::present,
                () -> nested.newSession());
    }

    public static <VALUE> Condition<Optional<VALUE>, VALUE> hasValue(PreservingCondition<? super VALUE> nested) {
        return compose("optional value: ", nested, OptionalConditions::present,
                () -> nested.preservingSession());
    }

    private static <VALUE> ConditionResult<VALUE> present(Optional<VALUE> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
