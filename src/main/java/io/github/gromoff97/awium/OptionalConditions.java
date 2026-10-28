package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.ConditionEvaluation;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.function.CheckedPredicate;
import java.util.Optional;

import static io.github.gromoff97.awium.internal.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.internal.ConditionEvaluation.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;

/**
 * Checks and selections for {@link Optional} sources. {@link #absent} returns the original empty
 * optional; {@link #present} returns its contained value. {@code hasValue(...)} first requires
 * presence, then returns the checked value or the nested condition's result.
 * A null observed optional is unsatisfied, including for {@code absent}.
 */
public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>> present =
            ConditionSupport.selected("optional is present", OptionalConditions::present);
    public static final PreservingCondition<Optional<?>> absent = ConditionSupport.preservingNonNull(
            "optional", "optional is absent", "optional was present", null, Optional::isEmpty);

    private OptionalConditions() {}

    /** Selects the present value if it matches, retaining the source's contained type. */
    public static <VALUE> SelectedCondition<Optional<? extends VALUE>> hasValue(CheckedPredicate<? super VALUE> predicate) {
        return hasValue(Conditions.matches(predicate));
    }

    public static <VALUE, RESULT> Condition.Typed<Optional<? extends VALUE>, RESULT> hasValue(Condition.Typed<? super VALUE, RESULT> nested) {
        return compose("optional value", nested, present);
    }

    public static <CHECKED> SelectedCondition<Optional<? extends CHECKED>> hasValue(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("optional value", nested, present));
    }

    public static <EXPECTED> SelectedCondition<Optional<? super EXPECTED>> hasValue(
            Condition.ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(ConditionSupport.compose("optional value", nested, present));
    }

    private static <VALUE> ConditionEvaluation<VALUE> present(Optional<? extends VALUE> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
