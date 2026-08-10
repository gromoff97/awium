package io.github.gromoff97.awium;

import java.util.Objects;

public abstract class Condition<S, R> {

    protected Condition() {
    }

    public abstract Evaluation<R> evaluate(S actual) throws Exception;

    public String description() {
        return "custom condition";
    }

    public final Explained<S, R> because(String explanation) {
        return ConditionDecorators.explain(this, explanation);
    }

    public final Explained<S, R> because(
            String format, Object... arguments) {
        return ConditionDecorators.explain(this, format, arguments);
    }

    public static final class Explained<S, R> {

        private final Condition<S, R> delegate;
        private final String explanation;

        Explained(Condition<S, R> delegate, String explanation) {
            this.delegate = Objects.requireNonNull(delegate);
            this.explanation = Objects.requireNonNull(explanation);
        }

        Condition<S, R> delegate() {
            return delegate;
        }

        String explanation() {
            return explanation;
        }
    }
}
