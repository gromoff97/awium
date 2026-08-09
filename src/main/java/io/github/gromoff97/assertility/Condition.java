package io.github.gromoff97.assertility;

public abstract class Condition<S, R> {

    protected Condition() {
    }

    public abstract Evaluation<R> evaluate(S actual) throws Exception;

    public String description() {
        return "custom condition";
    }

    public final ExplainedCondition<S, R> because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final ExplainedCondition<S, R> because(
            String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }
}
