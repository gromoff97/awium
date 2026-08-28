package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.engine.ConditionAssessment;
import io.github.gromoff97.awium.evaluation.ConditionEvaluation;
import io.github.gromoff97.awium.results.AwaitAttempt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.assertionUnsatisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.satisfied;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.uncontrolled;
import static io.github.gromoff97.awium.evaluation.ConditionEvaluation.unsatisfied;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class CapturedEvaluator<Observed, Result> implements Function<Observed, ConditionAssessment<List<Result>>> {

    private final List<Stage<Observed, Result>> stages;
    private final ArrayList<Result> results = new ArrayList<>();

    CapturedEvaluator(List<Stage<Observed, Result>> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public ConditionAssessment<List<Result>> apply(Observed actual) {
        boolean sequenceComplete = results.size() == stages.size();
        int evaluatedStageIndex = sequenceComplete ? stages.size() - 1 : results.size();
        ConditionAssessment<? extends Result> assessment;
        try {
            assessment = stages.get(evaluatedStageIndex).evaluator().apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return assessed(uncontrolled(failure), contextFor(evaluatedStageIndex));
        }
        if (assessment == null) {
            return assessed(uncontrolled(new NullPointerException("condition returned null ConditionAssessment")),
                    contextFor(evaluatedStageIndex));
        }
        ConditionEvaluation<? extends Result> evaluation = assessment.evaluation();
        if (evaluation == null) {
            return assessed(uncontrolled(new NullPointerException("condition returned null ConditionEvaluation")),
                    contextFor(evaluatedStageIndex));
        }
        return switch (evaluation) {
            case ConditionEvaluation.Satisfied<? extends Result> value -> sequenceComplete
                    ? refreshFinalResult(value.result(), evaluatedStageIndex)
                    : captureStageResult(value.result(), evaluatedStageIndex);
            case ConditionEvaluation.Unsatisfied<?> value ->
                    assessed(unsatisfied(value.mismatch()), contextFor(evaluatedStageIndex));
            case ConditionEvaluation.AssertionUnsatisfied<?> value ->
                    assessed(assertionUnsatisfied(value.mismatch(), value.cause()), contextFor(evaluatedStageIndex));
            case ConditionEvaluation.Uncontrolled<?> value ->
                    assessed(uncontrolled(value.cause()), contextFor(evaluatedStageIndex));
        };
    }

    private ConditionAssessment<List<Result>> captureStageResult(Result result, int evaluatedStageIndex) {
        results.add(result);
        if (results.size() < stages.size()) {
            return assessed(unsatisfied("waiting for sequence stage " + (results.size() + 1)),
                    contextFor(results.size(), evaluatedStageIndex));
        }
        return assessed(satisfied(capturedResults()), contextFor(evaluatedStageIndex));
    }

    private ConditionAssessment<List<Result>> refreshFinalResult(Result result, int evaluatedStageIndex) {
        results.set(results.size() - 1, result);
        return assessed(satisfied(capturedResults()), contextFor(evaluatedStageIndex));
    }

    private List<Result> capturedResults() {
        return results.stream().toList();
    }

    private AwaitAttempt.Context.Sequence contextFor(int stageIndex) {
        return contextFor(stageIndex, stageIndex);
    }

    private AwaitAttempt.Context.Sequence contextFor(int waitingStageIndex, int evaluatedStageIndex) {
        Stage<Observed, Result> waitingStage = stages.get(waitingStageIndex);
        return new AwaitAttempt.Context.Sequence(waitingStageIndex, stages.size(), evaluatedStageIndex + 1,
                waitingStage.expectation(), waitingStage.importance());
    }

    private static <Result> ConditionAssessment<List<Result>> assessed(ConditionEvaluation<List<Result>> evaluation,
            AwaitAttempt.Context context) {
        return new ConditionAssessment<>(evaluation, context);
    }

    record Stage<Observed, Result>(Function<? super Observed,
            ? extends ConditionAssessment<? extends Result>> evaluator,
            String expectation, String importance) {

        public Stage {
            requireNonNull(evaluator, "evaluator must not be null");
            requireNonNull(expectation, "expectation must not be null");
        }
    }
}
