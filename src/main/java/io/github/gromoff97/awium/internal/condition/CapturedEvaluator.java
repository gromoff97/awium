package io.github.gromoff97.awium.internal.condition;

import io.github.gromoff97.awium.condition.ConditionEvaluation;
import io.github.gromoff97.awium.results.AwaitAttempt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.condition.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.condition.ConditionEvaluation.unsatisfied;
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
            return ConditionEvaluation.<List<Result>>uncontrolled(failure)
                    .withContext(contextFor(evaluatedStageIndex));
        }
        if (evaluation == null) {
            return ConditionEvaluation.<List<Result>>uncontrolled(
                    new NullPointerException("condition returned null ConditionEvaluation"))
                    .withContext(contextFor(evaluatedStageIndex));
        }
        AwaitAttempt.Context context = evaluation.context();
        if (context instanceof AwaitAttempt.Context.Expectation expectation) {
            context = new AwaitAttempt.Context.Sequence(evaluatedStageIndex, stages.size(), evaluatedStageIndex + 1,
                    expectation.description(), stages.get(evaluatedStageIndex).metadata().explanation(), expectation.reference());
        } else if (evaluation instanceof ConditionEvaluation.Satisfied<?>
                || !(context instanceof AwaitAttempt.Context.Sequence)) {
            context = contextFor(evaluatedStageIndex);
        }
        return evaluation.withContext(context).continueIfSatisfied(value -> sequenceComplete
                ? refreshFinalResult(value, evaluatedStageIndex)
                : captureStageResult(value, evaluatedStageIndex));
    }

    private ConditionEvaluation<List<Result>> captureStageResult(Result result, int evaluatedStageIndex) {
        results.add(result);
        if (results.size() < stages.size()) {
            return ConditionEvaluation.<List<Result>>unsatisfied(
                    "waiting for sequence stage " + (results.size() + 1))
                    .withContext(contextFor(results.size(), evaluatedStageIndex));
        }
        return satisfied(capturedResults()).withContext(contextFor(results.size(), evaluatedStageIndex));
    }

    private ConditionEvaluation<List<Result>> refreshFinalResult(Result result, int evaluatedStageIndex) {
        results.set(results.size() - 1, result);
        return satisfied(capturedResults()).withContext(contextFor(results.size(), evaluatedStageIndex));
    }

    private List<Result> capturedResults() {
        return results.stream().toList();
    }

    private AwaitAttempt.Context.Sequence contextFor(int stageIndex) {
        return contextFor(stageIndex, stageIndex);
    }

    private AwaitAttempt.Context.Sequence contextFor(int capturedStages, int evaluatedStageIndex) {
        ConditionMetadata metadata = stages.get(Math.min(capturedStages, stages.size() - 1)).metadata();
        return new AwaitAttempt.Context.Sequence(capturedStages, stages.size(), evaluatedStageIndex + 1,
                metadata.description(), metadata.explanation(), metadata.reference());
    }

    record Stage<Observed, Result>(Function<? super Observed,
            ? extends ConditionEvaluation<? extends Result>> evaluator,
            ConditionMetadata metadata) {

        public Stage {
            requireNonNull(evaluator, "evaluator must not be null");
            requireNonNull(metadata, "metadata must not be null");
        }
    }
}
