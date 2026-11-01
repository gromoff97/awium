package io.github.gromoff97.awium.conditions;

import io.github.gromoff97.awium.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.conditions.internal.AbsentCondition;
import io.github.gromoff97.awium.conditions.internal.PresentCondition;
import io.github.gromoff97.awium.conditions.function.CheckedPredicate;
import java.util.Optional;

import static io.github.gromoff97.awium.AwaitCondition.ComparisonCondition;
import static io.github.gromoff97.awium.conditions.Conditions.matches;
import static io.github.gromoff97.awium.conditions.internal.NestedCondition.compose;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>> present = new PresentCondition();
    public static final PreservingCondition<Optional<?>> absent = new AbsentCondition();

    private OptionalConditions() {}

    public static <VALUE> SelectedCondition<Optional<? extends VALUE>> hasValue(CheckedPredicate<? super VALUE> predicate) {
        return hasValue(matches(predicate));
    }

    public static <VALUE, RESULT> Condition<Optional<? extends VALUE>, RESULT> hasValue(Condition<? super VALUE, RESULT> nested) {
        return compose("optional value", nested, present);
    }

    public static <CHECKED> SelectedCondition<Optional<? extends CHECKED>> hasValue(
            PreservingCondition<? super CHECKED> nested) {
        return new SelectedCondition<>(compose("optional value", nested, present)) {};
    }

    public static <EXPECTED> SelectedCondition<Optional<? super EXPECTED>> hasValue(
            ComparisonCondition<EXPECTED> nested) {
        return new SelectedCondition<>(compose("optional value", nested, present)) {};
    }

}
