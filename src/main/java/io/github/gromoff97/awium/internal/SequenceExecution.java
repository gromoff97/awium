package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.result.ConditionResult;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static java.lang.Math.min;

public final class SequenceExecution<OBSERVED, RESULT> {

    private final List<ConditionExecution<OBSERVED, RESULT>> stages;
    private final List<RESULT> results = new ArrayList<>();

    public SequenceExecution(List<ConditionExecution<OBSERVED, RESULT>> stages) {
        this.stages = List.copyOf(stages);
    }

    int stageNumber() {
        return min(results.size() + 1, stages.size());
    }

    int totalStages() {
        return stages.size();
    }

    int completedStages() {
        return results.size();
    }

    public List<RESULT> values() {
        // Captured nulls are valid; List.copyOf would reject them.
        return results.stream().toList();
    }

    ConditionResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        var stage = stages.get(stageNumber() - 1);
        // Persistence rechecks the final stage, retaining all earlier values.
        if (results.size() == stages.size()) results.removeLast();
        var evaluation = stage.probe(actual);
        if (evaluation instanceof Satisfied<? extends RESULT> success) {
            results.add(success.value());
        }
        return evaluation;
    }
}
