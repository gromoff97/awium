package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.condition;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.equal;
import static io.github.gromoff97.awium.conditioning.providers.ConditionProvider.preservingCondition;

public final class ObjectConditionProvider {

    public static final Condition<Object, Void> isNull = condition("value is null",
            actual -> actual == null ? satisfied(null) : unsatisfied("value was not null"));

    public static final PreservingCondition<Object> isNotNull = preservingCondition(() -> "value is not null",
            actual -> actual != null ? satisfied(actual) : unsatisfied("value was null"));

    private ObjectConditionProvider() {
        throw new AssertionError("Utility class");
    }

    public static PreservingCondition<Object> equalTo(Object expected) {
        return equality(expected, true);
    }

    public static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return equality(unexpected, false);
    }

    private static PreservingCondition<Object> equality(Object operand, boolean expectedEqual) {
        return preservingCondition(() -> "value " + (expectedEqual ? "equals " : "does not equal ") + operand,
                actual -> equal(actual, operand) == expectedEqual
                        ? satisfied(actual)
                        : unsatisfied("value was " + (expectedEqual ? "not equal" : "equal")));
    }
}
