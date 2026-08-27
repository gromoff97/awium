package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public sealed interface ConditionStage<S, R> permits ConditionStage.ResultStage, Condition.PreservingStage,
        Condition.SelectedStage, Condition.SelectedSequenceStage {

    String description();

    String explanation();

    Supplier<? extends Function<? super S, ? extends Evaluation<? extends R>>> evaluatorFactory();

    default Function<? super S, ? extends Evaluation<? extends R>> newEvaluator() {
        return requireNonNull(evaluatorFactory().get(), "evaluator must not be null");
    }

    sealed interface ResultStage<S, R> extends ConditionStage<S, R> permits Condition {
    }
}
