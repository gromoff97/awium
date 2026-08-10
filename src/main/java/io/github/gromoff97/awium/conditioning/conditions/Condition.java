package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.Objects;

public abstract class Condition<S, R> {

    protected Condition() {
    }

    public abstract Evaluation<R> evaluate(S actual) throws Exception;

    public abstract String description();

    public final ExplainedCondition<S, R> because(String explanation) {
        return new ExplainedCondition<>(this,
                RuntimeCondition.literalExplanation(explanation));
    }

    public final ExplainedCondition<S, R> because(
            String format, Object... arguments) {
        return new ExplainedCondition<>(this,
                RuntimeCondition.formattedExplanation(format, arguments));
    }

    public static final class ExplainedCondition<S, R> {

        private final Condition<S, R> delegate;
        private final String explanation;

        private ExplainedCondition(Condition<S, R> delegate,
                String explanation) {
            this.delegate = Objects.requireNonNull(delegate);
            this.explanation = Objects.requireNonNull(explanation);
        }

        public Condition<S, R> delegate() {
            return delegate;
        }

        public String explanation() {
            return explanation;
        }
    }
}
