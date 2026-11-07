package io.github.gromoff97.awium.conditions.composition;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.dsl.AwaitCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.ApplicableCondition;
import io.github.gromoff97.awium.dsl.AwaitCondition.SelectedCondition;
import io.github.gromoff97.awium.dsl.TypedCondition;

public final class CompositionConditions {
    private CompositionConditions() {}

    // Family factories supply the type bounds; this package must remain unexported.
    public static <INPUT, VALUE> TypedCondition<INPUT, VALUE> compose(
            String selection, ApplicableCondition<?, ?> nested, AwaitCondition extraction) {
        return new NestedCondition<>(selection, nested, extraction);
    }

    public static <INPUT, RESULT> TypedCondition<INPUT, RESULT> compose(
            String selection, Condition<?, RESULT> nested, AwaitCondition extraction) {
        return new NestedCondition<>(selection, TypedCondition.from(nested), extraction);
    }

    public static <INPUT> SelectedCondition<INPUT> selected(Condition<INPUT, ?> nested) {
        return new SelectedNestedCondition<>(nested);
    }
}
