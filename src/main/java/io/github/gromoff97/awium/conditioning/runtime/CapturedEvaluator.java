package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class CapturedEvaluator<Observed, Result> implements Function<Observed, Evaluation<List<Result>>> {

    private final List<Stage<Observed, Result>> stages;
    private final ArrayList<Result> results = new ArrayList<>();

    CapturedEvaluator(List<Stage<Observed, Result>> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public Evaluation<List<Result>> apply(Observed actual) {
        boolean sequenceComplete = results.size() == stages.size();
        int evaluatedStageIndex = sequenceComplete ? stages.size() - 1 : results.size();
        Evaluation<? extends Result> evaluation;
        try {
            evaluation = stages.get(evaluatedStageIndex).evaluator().apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return Evaluation.<List<Result>>uncontrolled(failure).withContext(contextFor(evaluatedStageIndex));
        }
        if (evaluation == null) {
            return Evaluation.<List<Result>>uncontrolled(new NullPointerException("condition returned null Evaluation"))
                    .withContext(contextFor(evaluatedStageIndex));
        }
        Evaluation<? extends Result> contextual = evaluation.withContext(contextFor(evaluatedStageIndex));
        return sequenceComplete
                ? contextual.continueIfSatisfied(this::refreshFinalResult)
                : contextual.continueIfSatisfied(result -> captureStageResult(result, evaluatedStageIndex));
    }

    private Evaluation<List<Result>> captureStageResult(Result result, int evaluatedStageIndex) {
        results.add(result);
        if (results.size() < stages.size()) {
            return Evaluation.<List<Result>>unsatisfied("waiting for sequence stage " + (results.size() + 1))
                    .withContext(contextFor(results.size(), evaluatedStageIndex));
        }
        return satisfied(capturedResults());
    }

    private Evaluation<List<Result>> refreshFinalResult(Result result) {
        results.set(results.size() - 1, result);
        return satisfied(capturedResults());
    }

    private List<Result> capturedResults() {
        return results.stream().toList();
    }

    private Evaluation.Context.Sequence contextFor(int stageIndex) {
        return contextFor(stageIndex, stageIndex);
    }

    private Evaluation.Context.Sequence contextFor(int waitingStageIndex, int evaluatedStageIndex) {
        Stage<Observed, Result> waitingStage = stages.get(waitingStageIndex);
        return new Evaluation.Context.Sequence(waitingStageIndex, stages.size(),
                evaluatedStageIndex + 1, waitingStage.expectation(), waitingStage.importance());
    }

    record Stage<Observed, Result>(Function<? super Observed,
            ? extends Evaluation<? extends Result>> evaluator,
            String expectation, String importance) {

        public Stage {
            requireNonNull(evaluator, "evaluator must not be null");
            requireNonNull(expectation, "expectation must not be null");
        }
    }
}
