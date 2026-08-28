package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.evaluation.ConditionEvaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class CapturedEvaluator<Observed, Result> implements Function<Observed, ConditionEvaluation<List<Result>>> {

    private final List<Stage<Observed, Result>> stages;
    private final ArrayList<Result> results = new ArrayList<>();

    CapturedEvaluator(List<Stage<Observed, Result>> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public ConditionEvaluation<List<Result>> apply(Observed actual) {
        boolean sequenceComplete = results.size() == stages.size();
        int evaluatedStageIndex = sequenceComplete ? stages.size() - 1 : results.size();
        ConditionEvaluation<? extends Result> evaluation;
        try {
            evaluation = stages.get(evaluatedStageIndex).evaluator().apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return ConditionEvaluation.<List<Result>>uncontrolled(failure).withContext(contextFor(evaluatedStageIndex));
        }
        if (evaluation == null) {
            return ConditionEvaluation.<List<Result>>uncontrolled(new NullPointerException("condition returned null ConditionEvaluation"))
                    .withContext(contextFor(evaluatedStageIndex));
        }
        ConditionEvaluation<? extends Result> contextual = evaluation.withContext(contextFor(evaluatedStageIndex));
        return sequenceComplete
                ? contextual.continueIfSatisfied(this::refreshFinalResult)
                : contextual.continueIfSatisfied(result -> captureStageResult(result, evaluatedStageIndex));
    }

    private ConditionEvaluation<List<Result>> captureStageResult(Result result, int evaluatedStageIndex) {
        results.add(result);
        if (results.size() < stages.size()) {
            return ConditionEvaluation.<List<Result>>unsatisfied("waiting for sequence stage " + (results.size() + 1))
                    .withContext(contextFor(results.size(), evaluatedStageIndex));
        }
        return satisfied(capturedResults());
    }

    private ConditionEvaluation<List<Result>> refreshFinalResult(Result result) {
        results.set(results.size() - 1, result);
        return satisfied(capturedResults());
    }

    private List<Result> capturedResults() {
        return results.stream().toList();
    }

    private ConditionEvaluation.Context.Sequence contextFor(int stageIndex) {
        return contextFor(stageIndex, stageIndex);
    }

    private ConditionEvaluation.Context.Sequence contextFor(int waitingStageIndex, int evaluatedStageIndex) {
        Stage<Observed, Result> waitingStage = stages.get(waitingStageIndex);
        return new ConditionEvaluation.Context.Sequence(waitingStageIndex, stages.size(),
                evaluatedStageIndex + 1, waitingStage.expectation(), waitingStage.importance());
    }

    record Stage<Observed, Result>(Function<? super Observed,
            ? extends ConditionEvaluation<? extends Result>> evaluator,
            String expectation, String importance) {

        public Stage {
            requireNonNull(evaluator, "evaluator must not be null");
            requireNonNull(expectation, "expectation must not be null");
        }
    }
}
