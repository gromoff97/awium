package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;

public final class ObjectConditionProvider {

    public static final Condition<Object, Void> isNull =
            condition("value to be null", actual ->
                    actual == null
                            ? satisfied(null)
                            : unsatisfied("value was not null"));

    public static final PreservingCondition<Object> isNotNull =
            PreservingCondition.of(new RuntimeCondition<>(actual ->
                actual != null ? satisfied(actual)
                        : unsatisfied("value was null"),
                () -> "value to be non-null", null));

    private ObjectConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return PreservingCondition.of(new RuntimeCondition<>(actual ->
                equal(actual, expected)
                        ? satisfied(actual)
                        : unsatisfied("value was not equal"),
                () -> "value equal to " + expected, null));
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return PreservingCondition.of(new RuntimeCondition<>(actual ->
                !equal(actual, unexpected)
                        ? satisfied(actual)
                        : unsatisfied("value was equal"),
                () -> "value not equal to " + unexpected, null));
    }
}
