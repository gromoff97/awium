package io.github.gromoff97.awium.conditioning.providers;

import io.github.gromoff97.awium.conditioning.Evaluation;
import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.RuntimeCondition;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;
import static io.github.gromoff97.awium.conditioning.ValueEquality.equal;

final class ObjectConditionProvider {

    private ObjectConditionProvider() {
        throw new AssertionError("Utility class");
    }

    static Condition<Object, Void> isNull() {
        return new Condition<>() {
            @Override
            public Evaluation<Void> evaluate(Object actual) {
                return actual == null ? satisfied(null)
                        : unsatisfied("value was not null");
            }

            @Override
            public String description() {
                return "value to be null";
            }
        };
    }

    static PreservingCondition<Object> isNotNull() {
        return PreservingCondition.of(new RuntimeCondition<>(actual ->
                actual != null ? satisfied(actual)
                        : unsatisfied("value was null"),
                () -> "value to be non-null", null));
    }

    static PreservingCondition<Object> equalTo(Object expected) {
        return PreservingCondition.of(new RuntimeCondition<>(actual ->
                equal(actual, expected)
                        ? satisfied(actual)
                        : unsatisfied("value was not equal"),
                () -> "value equal to " + expected, null));
    }

    static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return PreservingCondition.of(new RuntimeCondition<>(actual ->
                !equal(actual, unexpected)
                        ? satisfied(actual)
                        : unsatisfied("value was equal"),
                () -> "value not equal to " + unexpected, null));
    }
}
