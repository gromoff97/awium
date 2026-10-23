package io.github.gromoff97.awium;

import java.util.List;
import java.util.stream.Stream;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;

/** Creates ordered conditions and owns the captured values of one execution. */
final class SequenceExecution<OBSERVED, RESULT> {

    private final List<? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> stages;
    private List<RESULT> results = List.of();

    SequenceExecution(List<? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> stages) {
        this.stages = stages;
    }

    AttemptResult<List<RESULT>> step(OBSERVED actual) {
        int stage = Math.min(results.size(), stages.size() - 1);
        var evaluation = stages.get(stage).probe(actual);
        return switch (evaluation) {
            case AttemptResult.Evaluated<? extends RESULT> value -> switch (value.result()) {
                case ConditionResult.Satisfied<? extends RESULT> success -> {
                    results = Stream.concat(results.stream().limit(stage), Stream.of(success.result())).toList();
                    int nextStage = Math.min(stage + 1, stages.size() - 1);
                    var progress = new AttemptRecord.Progress(results.size(), stages.size(), stage + 1,
                            stages.get(nextStage).condition);
                    yield new AttemptResult.Evaluated<>(results.size() == stages.size() ? satisfied(results)
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
        return evaluation.progress() != null ? evaluation.progress()
                : new AttemptRecord.Progress(stage, stages.size(), stage + 1,
                        evaluation.conditionOr(stages.get(stage).condition));
    }
}
