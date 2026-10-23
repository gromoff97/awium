package io.github.gromoff97.awium;

import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.ConditionResult.satisfied;
import static io.github.gromoff97.awium.ConditionResult.unsatisfied;

/** Creates ordered conditions and owns the captured values of one execution. */
final class SequenceExecution<OBSERVED, RESULT> {

    private final List<? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> stages;
    private State<RESULT> state;

    SequenceExecution(List<? extends ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT>> stages) {
        this.stages = stages;
        state = new State<>(stages.size(), List.of());
    }

    AttemptResult<List<RESULT>> step(OBSERVED actual) {
        var before = state;
        var evaluation = stages.get(before.currentStage()).probe(actual);
        var after = before.transition(evaluation);
        state = after;
        return describeAttempt(before, after, evaluation);
    }

    private AttemptResult<List<RESULT>> describeAttempt(State<RESULT> before, State<RESULT> after,
            AttemptResult<? extends RESULT> evaluation) {
        int evaluatedStage = before.currentStage();
        var progress = evaluation.progress();
        if (evaluation instanceof AttemptResult.Evaluated<?> value
                && value.result() instanceof ConditionResult.Satisfied<?>) {
            progress = new AttemptRecord.Progress(after.results().size(), stages.size(), evaluatedStage + 1,
                    stages.get(after.currentStage()).condition);
        } else if (progress == null) {
            progress = new AttemptRecord.Progress(evaluatedStage, stages.size(), evaluatedStage + 1,
                    evaluation.conditionOr(stages.get(evaluatedStage).condition));
        }
        return switch (evaluation) {
            case AttemptResult.Evaluated<?> value -> new AttemptResult.Evaluated<>(
                    value.result().continueIfSatisfied(ignored -> after.complete()
                            ? satisfied(after.results())
                            : unsatisfied("waiting for sequence stage " + (after.currentStage() + 1))),
                    null, progress);
            case AttemptResult.Uncontrolled<?> failure -> new AttemptResult.Uncontrolled<>(failure.cause(), null, progress);
        };
    }

    private record State<RESULT>(int totalStages, List<RESULT> results) {

        int currentStage() {
            return Math.min(results.size(), totalStages - 1);
        }

        boolean complete() {
            return results.size() == totalStages;
        }

        State<RESULT> transition(AttemptResult<? extends RESULT> result) {
            if (!(result instanceof AttemptResult.Evaluated<? extends RESULT> value)
                    || !(value.result() instanceof ConditionResult.Satisfied<? extends RESULT> satisfied)) {
                return this;
            }
            var next = new ArrayList<>(results);
            if (complete()) {
                next.set(currentStage(), satisfied.result());
            } else {
                next.add(satisfied.result());
            }
            return new State<>(totalStages, next.stream().toList());
        }
    }
}
