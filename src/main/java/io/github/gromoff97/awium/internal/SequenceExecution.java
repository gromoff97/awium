package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class SequenceExecution<OBSERVED, RESULT> {

    private final Condition definition;
    private final List<ConditionExecution<OBSERVED, RESULT>> stages;
    private final List<RESULT> results = new ArrayList<>();

    public SequenceExecution(ConditionExecution<OBSERVED, RESULT> attempt) {
        this(attempt.condition, List.of(attempt));
    }

    private SequenceExecution(Condition definition, List<ConditionExecution<OBSERVED, RESULT>> stages) {
        this.definition = definition;
        this.stages = stages;
    }

    public static <OBSERVED> SequenceExecution<OBSERVED, OBSERVED> captured(Condition.Captured sequence,
            Function<Condition.Check, ConditionExecution<OBSERVED, OBSERVED>> bind) {
        return new SequenceExecution<>(sequence, sequence.stages().stream().map(bind).toList());
    }

    int stageNumber() {
        return Math.min(results.size() + 1, stages.size());
    }

    Condition definition() {
        return definition;
    }

    int completedStages() {
        return results.size();
    }

    boolean isComplete() {
        return results.size() == stages.size();
    }

    public List<RESULT> values() {
        // Captured nulls are valid; List.copyOf would reject them.
        return results.stream().toList();
    }

    public List<Condition> conditions() {
        return stages.stream().<Condition>map(stage -> stage.condition).toList();
    }

    AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        var stage = stages.get(stageNumber() - 1);
        // Persistence rechecks the final stage, retaining all earlier values.
        if (isComplete()) results.removeLast();
        var evaluation = stage.probe(actual);
        if (evaluation instanceof AttemptResult.Satisfied<? extends RESULT> success) {
            results.add(success.value());
        }
        return evaluation;
    }
}
