package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;

@SuppressWarnings("removal")
final class CapturedEvaluator<Observed, Result> implements Function<Observed, ConditionResult<List<Result>>> {

    private final List<ConditionSession<? super Observed, ? extends Result>> stages;
    private final ArrayList<Result> results = new ArrayList<>();

    CapturedEvaluator(List<? extends ConditionSession<? super Observed, ? extends Result>> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public ConditionResult<List<Result>> apply(Observed actual) {
        boolean sequenceComplete = results.size() == stages.size();
        int evaluatedStageIndex = sequenceComplete ? stages.size() - 1 : results.size();
        ConditionResult<? extends Result> evaluation;
        try {
            evaluation = stages.get(evaluatedStageIndex).apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return ConditionResult.<List<Result>>uncontrolled(failure)
                    .withContext(contextFor(evaluatedStageIndex));
        }
        if (evaluation == null) {
            return ConditionResult.<List<Result>>uncontrolled(
                    new NullPointerException("condition returned null ConditionResult"))
                    .withContext(contextFor(evaluatedStageIndex));
        }
        ConditionResult.Context context = evaluation.context();
        if (context instanceof ConditionResult.Context.Expectation expectation) {
            context = new ConditionResult.Context.Sequence(evaluatedStageIndex, stages.size(), evaluatedStageIndex + 1,
                    expectation.description(), stages.get(evaluatedStageIndex).metadata.explanation(), expectation.reference());
        } else if (evaluation instanceof ConditionResult.Satisfied<?>
                || !(context instanceof ConditionResult.Context.Sequence)) {
            context = contextFor(evaluatedStageIndex);
        }
        return evaluation.withContext(context).continueIfSatisfied(value -> sequenceComplete
                ? refreshFinalResult(value, evaluatedStageIndex)
                : captureStageResult(value, evaluatedStageIndex));
    }

    private ConditionResult<List<Result>> captureStageResult(Result result, int evaluatedStageIndex) {
        results.add(result);
        if (results.size() < stages.size()) {
            return ConditionResult.<List<Result>>unsatisfied(
                    "waiting for sequence stage " + (results.size() + 1))
                    .withContext(contextFor(results.size(), evaluatedStageIndex));
        }
        return satisfied(capturedResults()).withContext(contextFor(results.size(), evaluatedStageIndex));
    }

    private ConditionResult<List<Result>> refreshFinalResult(Result result, int evaluatedStageIndex) {
        results.set(results.size() - 1, result);
        return satisfied(capturedResults()).withContext(contextFor(results.size(), evaluatedStageIndex));
    }

    private List<Result> capturedResults() {
        return results.stream().toList();
    }

    private ConditionResult.Context.Sequence contextFor(int stageIndex) {
        return contextFor(stageIndex, stageIndex);
    }

    private ConditionResult.Context.Sequence contextFor(int capturedStages, int evaluatedStageIndex) {
        ConditionMetadata metadata = stages.get(Math.min(capturedStages, stages.size() - 1)).metadata;
        return new ConditionResult.Context.Sequence(capturedStages, stages.size(), evaluatedStageIndex + 1,
                metadata.description(), metadata.explanation(), metadata.reference());
    }

}
