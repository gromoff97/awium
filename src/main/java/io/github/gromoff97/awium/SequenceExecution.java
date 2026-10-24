package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;

/** Evaluates ordered stages and owns their progress and captured values for one execution. */
final class SequenceExecution<OBSERVED, RESULT> {

    private final List<? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> stages;
    private final List<RESULT> results = new ArrayList<>();

    SequenceExecution(Stream<? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> stages) {
        this.stages = stages.toList();
    }

    AttemptResult<List<RESULT>> step(OBSERVED actual) {
        // Recheck the final stage after completion.
        if (results.size() == stages.size()) {
            results.removeLast();
        }
        int stage = results.size();
        var evaluation = stages.get(stage).probe(actual);
        return switch (evaluation) {
            case AttemptResult.Evaluated<? extends RESULT> value -> switch (value.result()) {
                case ConditionResult.Satisfied<? extends RESULT> success -> {
                    results.add(success.result());
                    int nextStage = Math.min(stage + 1, stages.size() - 1);
                    var progress = new AttemptRecord.Progress(results.size(), stages.size(), stage + 1,
                            stages.get(nextStage).condition);
                    yield new AttemptResult.Evaluated<>(results.size() == stages.size() ? satisfied(results.stream().toList())
                            : unsatisfied("waiting for sequence stage " + (nextStage + 1)), null, progress);
                }
                case ConditionResult.Unsatisfied<?> mismatch -> new AttemptResult.Evaluated<>(
                        new ConditionResult.Unsatisfied<>(mismatch.mismatch(), mismatch.cause()), null,
                        progress(evaluation, stage));
            };
            case AttemptResult.Uncontrolled<?> failure -> new AttemptResult.Uncontrolled<>(
                    failure.cause(), null, progress(evaluation, stage));
        };
    }

    private AttemptRecord.Progress progress(AttemptResult<?> evaluation, int stage) {
        return new AttemptRecord.Progress(stage, stages.size(), stage + 1,
                evaluation.conditionOr(stages.get(stage).condition));
    }
}
