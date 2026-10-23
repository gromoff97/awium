package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import java.util.Optional;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>> present =
            ConditionSupport.selected("optional is present", OptionalConditions::present);
    public static final PreservingCondition<Optional<?>> absent = ConditionSupport.preservingNonNull(
            "optional", "optional is absent", "optional was present", Optional::isEmpty);

    private OptionalConditions() {}

    public static <VALUE, RESULT> Condition<Optional<? extends VALUE>, RESULT> hasValue(Condition<? super VALUE, ? extends RESULT> nested) {
        return compose("optional value: ", required(nested), null, OptionalConditions::present,
                nested::newAttempt);
    }

    public static <VALUE> SelectedCondition<Optional<? extends VALUE>> hasValue(PreservingCondition<? super VALUE> nested) {
        return new SelectedCondition<>(compose("optional value: ", required(nested).definition, null, OptionalConditions::present,
                nested::preservingAttempt));
    }

    public static <EXPECTED> SelectedCondition<Optional<? super EXPECTED>> hasValue(ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("optional value: ", required(nested).definition, null, OptionalConditions::present,
                nested.definition::preservingAttempt));
    }

    private static <VALUE> ConditionResult<VALUE> present(Optional<? extends VALUE> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
