package io.github.gromoff97.awium.conditions.optional;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.dsl.AwaitCondition.PreservingCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.dsl.TypedCondition;
import io.github.gromoff97.awium.function.checked.CheckedPredicate;
import java.util.Optional;

import static io.github.gromoff97.awium.conditions.common.CommonConditions.matches;
import static io.github.gromoff97.awium.conditions.composition.CompositionConditions.compose;
import static io.github.gromoff97.awium.conditions.composition.CompositionConditions.selected;
import static io.github.gromoff97.awium.dsl.AwaitCondition.ComparisonCondition;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>> present = new PresentCondition();
    public static final PreservingCondition<Optional<?>> absent = new AbsentCondition();

    private OptionalConditions() {}

    public static <VALUE> SelectedCondition<Optional<? extends VALUE>> hasValue(CheckedPredicate<? super VALUE> predicate) {
        return hasValue(matches(predicate));
    }

    public static <VALUE, RESULT> TypedCondition<Optional<? extends VALUE>, RESULT> hasValue(Condition<? super VALUE, RESULT> nested) {
        return compose("optional value", nested, present);
    }

    public static <CHECKED> SelectedCondition<Optional<? extends CHECKED>> hasValue(
            PreservingCondition<? super CHECKED> nested) {
        return selected(compose("optional value", nested, present));
    }

    public static <EXPECTED> SelectedCondition<Optional<? super EXPECTED>> hasValue(
            ComparisonCondition<EXPECTED> nested) {
        return selected(compose("optional value", nested, present));
    }
}
