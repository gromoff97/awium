package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Owns stage transitions and captured values for one wait, including a single-check wait. */
public final class SequenceExecution<OBSERVED, RESULT> {

    private final Condition.Captured sequence;
    private final List<ConditionAwaitAttempt<OBSERVED, RESULT>> stages;
    private final List<RESULT> results = new ArrayList<>();
    private Condition checkedCondition;

    public SequenceExecution(ConditionAwaitAttempt<OBSERVED, RESULT> attempt) {
        this(null, List.of(attempt));
    }

    private SequenceExecution(Condition.Captured sequence, List<ConditionAwaitAttempt<OBSERVED, RESULT>> stages) {
        this.sequence = sequence;
        this.stages = stages;
    }

    public static <OBSERVED> SequenceExecution<OBSERVED, OBSERVED> captured(Condition.Captured sequence,
            Function<Condition.Check, ConditionAwaitAttempt<OBSERVED, OBSERVED>> bind) {
        return new SequenceExecution<>(sequence, sequence.stages().stream().map(bind).toList());
    }

    int stageNumber() {
        return Math.min(results.size() + 1, stages.size());
    }

    Condition condition(int stageNumber) {
        return stages.get(stageNumber - 1).condition;
    }

    Condition checkedCondition() {
        return checkedCondition;
    }

    AttemptRecord.Progress progress(int stageNumber) {
        return sequence == null ? null : new AttemptRecord.Progress(sequence, stageNumber, results.size());
    }

    boolean complete() {
        return results.size() == stages.size();
    }

    public List<RESULT> values() {
        // Captured nulls are valid; List.copyOf would reject them.
        return results.stream().toList();
    }

    AttemptResult step(OBSERVED actual) {
        var stage = stages.get(stageNumber() - 1);
        // Persistence rechecks the final stage, retaining all earlier values.
        if (complete()) results.removeLast();
        var evaluation = stage.probe(actual);
        checkedCondition = evaluation.diagnosticCondition(stage.condition);
        if (evaluation instanceof ConditionEvaluation.Satisfied<? extends RESULT> success) {
            results.add(success.value());
        }
        return evaluation.status();
    }
}
