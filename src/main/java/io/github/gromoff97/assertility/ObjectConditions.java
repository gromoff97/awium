package io.github.gromoff97.assertility;

final class ObjectConditions {

    private ObjectConditions() {
    }

    static Condition<Object, Void> isNull() {
        return new Condition<>() {
            @Override
            public Evaluation<Void> evaluate(Object actual) {
                return actual == null ? Evaluation.satisfied(null)
                        : Evaluation.unsatisfied("value was not null");
            }

            @Override
            public String description() {
                return "value to be null";
            }
        };
    }

    static PreservingCondition<Object> isNotNull() {
        return new PreservingCondition<>(new ConditionRuntime<>(actual ->
                actual != null ? Evaluation.satisfied(actual)
                        : Evaluation.unsatisfied("value was null"),
                () -> "value to be non-null", null));
    }

    static PreservingCondition<Object> equalTo(Object expected) {
        return new PreservingCondition<>(new ConditionRuntime<>(actual ->
                ValueEquality.equal(actual, expected)
                        ? Evaluation.satisfied(actual)
                        : Evaluation.unsatisfied("value was not equal"),
                () -> "value equal to " + expected, null));
    }

    static PreservingCondition<Object> notEqualTo(Object unexpected) {
        return new PreservingCondition<>(new ConditionRuntime<>(actual ->
                !ValueEquality.equal(actual, unexpected)
                        ? Evaluation.satisfied(actual)
                        : Evaluation.unsatisfied("value was equal"),
                () -> "value not equal to " + unexpected, null));
    }
}
