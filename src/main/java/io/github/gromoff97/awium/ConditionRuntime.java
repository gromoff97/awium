package io.github.gromoff97.awium;

import java.util.Objects;

record ConditionRuntime<S, R>(
        Evaluator<S, R> evaluator,
        Description description,
        String explanation) {

    @FunctionalInterface
    interface Evaluator<S, R> {
        Evaluation<R> evaluate(S actual) throws Exception;
    }

    @FunctionalInterface
    interface Description {
        String get();
    }

    ConditionRuntime {
        Objects.requireNonNull(evaluator);
        Objects.requireNonNull(description);
    }

    Evaluation<R> evaluate(S actual) throws Exception {
        return evaluator.evaluate(actual);
    }

    ConditionRuntime<S, R> explained(String value) {
        return new ConditionRuntime<>(evaluator, description, value);
    }
}
