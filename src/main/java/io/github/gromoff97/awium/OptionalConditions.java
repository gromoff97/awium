package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.function.CheckedPredicate;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.Optional;

import static io.github.gromoff97.awium.Condition.ComparisonCondition;
import static io.github.gromoff97.awium.Condition.Typed;
import static io.github.gromoff97.awium.ConditionSupport.compose;
import static io.github.gromoff97.awium.ConditionSupport.preservingNonNull;
import static io.github.gromoff97.awium.ConditionSupport.selected;
import static io.github.gromoff97.awium.Conditions.matches;
import static io.github.gromoff97.awium.result.ConditionResult.satisfied;
import static io.github.gromoff97.awium.result.ConditionResult.unsatisfied;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>> present =
            selected("optional is present", OptionalConditions::present);
    public static final PreservingCondition<Optional<?>> absent = preservingNonNull(
            "optional", "optional is absent", "optional was present", null, Optional::isEmpty);

    private OptionalConditions() {}

    public static <VALUE> SelectedCondition<Optional<? extends VALUE>> hasValue(CheckedPredicate<? super VALUE> predicate) {
        return hasValue(matches(predicate));
    }

    public static <VALUE, RESULT> Typed<Optional<? extends VALUE>, RESULT> hasValue(Typed<? super VALUE, RESULT> nested) {
        return compose("optional value", nested, present);
    }

    public static <CHECKED> SelectedCondition<Optional<? extends CHECKED>> hasValue(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(compose("optional value", nested, present));
    }

    public static <EXPECTED> SelectedCondition<Optional<? super EXPECTED>> hasValue(
            ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("optional value", nested, present));
    }

    private static <VALUE> ConditionResult<VALUE> present(Optional<? extends VALUE> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
