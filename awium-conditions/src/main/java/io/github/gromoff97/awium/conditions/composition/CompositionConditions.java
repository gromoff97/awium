package io.github.gromoff97.awium.conditions.composition;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.Condition;

public final class CompositionConditions {
    private CompositionConditions() {}

    // Family factories supply the type bounds; this package must remain unexported.
    public static <INPUT, VALUE> Condition<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, AwaitCondition extraction) {
        return new NestedCondition<INPUT, VALUE, VALUE>(selection, nested, extraction);
    }

    public static <INPUT, VALUE, RESULT> Condition<INPUT, RESULT> compose(
            String selection, Condition<? super VALUE, RESULT> nested, AwaitCondition extraction) {
        return new NestedCondition<INPUT, VALUE, RESULT>(selection, nested, extraction);
    }

    public static <INPUT> SelectedCondition<INPUT> selected(Condition<INPUT, ?> nested) {
        return new SelectedNestedCondition<>(nested);
    }
}
