package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.PreservingCondition;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SelectedCondition;
import io.github.gromoff97.awium.Condition.SourceCondition;
import java.util.Optional;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;
import static io.github.gromoff97.awium.ConditionSupport.compose;

public final class OptionalConditions {

    public static final SelectedCondition<Optional<?>, Object, Reference<?>> present =
            ConditionSupport.selected("optional is present", OptionalConditions::present);
    public static final PreservingCondition<Optional<?>> absent = ConditionSupport.preservingNonNull(
            "optional", "optional is absent", "optional was present", Optional::isEmpty);

    private OptionalConditions() {}

    /**
     * Configures an optional wait, retaining its contained value type for selections.
     * @param <VALUE> contained value type
     * @param source repeatedly invoked source
     * @return immutable wait configuration
     */
    public static <VALUE> Await<Optional<VALUE>, VALUE, Void> await(Callable<? extends Optional<VALUE>> source) {
        return new Await<>(source, WaitConfiguration.DEFAULT);
    }

    /** Selects the present value if it matches, retaining the source's contained type. */
    public static <VALUE> SelectedCondition<Optional<?>, VALUE, Reference<?>> hasValue(CheckedPredicate<? super VALUE> predicate) {
        return hasValue(Conditions.matches(predicate));
    }

    public static <VALUE, RESULT> Condition.Typed<Optional<? extends VALUE>, RESULT> hasValue(Condition.Typed<? super VALUE, RESULT> nested) {
        return compose("optional value: ", required(nested), null, OptionalConditions::present,
                () -> new ConditionAwaitAttempt<>(nested));
    }

    public static <CHECKED, REFERENCE> SelectedCondition<Optional<?>, CHECKED, REFERENCE> hasValue(
            SourceCondition<CHECKED, REFERENCE, ?> nested) {
        return new SelectedCondition<>(compose("optional value: ", required(nested), null, OptionalConditions::present,
                () -> ConditionAwaitAttempt.bindSelectionCheck(nested)));
    }

    private static <VALUE> ConditionResult<VALUE> present(Optional<? extends VALUE> actual) {
        if (actual == null) {
            return unsatisfied("optional was null");
        }
        return actual.isPresent() ? satisfied(actual.orElseThrow()) : unsatisfied("optional was empty");
    }

}
