package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.execution.AbstractConditionRun;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.execution.ConditionRun;
import io.github.gromoff97.awium.execution.DiagnosticText;
import io.github.gromoff97.awium.execution.ExecutionStep;
import io.github.gromoff97.awium.result.ConditionResult;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.execution.DiagnosticText.field;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.ConditionResult.Satisfied;
import static java.lang.Math.min;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

final class CapturedCondition<CHECK extends AwaitCondition> implements AwaitCondition.CollectingCondition<CHECK> {

    private final List<AwaitCondition> stages;

    CapturedCondition(AwaitCondition[] conditions) {
        stages = stream(requireNonNull(conditions, "conditions must not be null"))
                .map(condition -> requireNonNull(condition, "condition must not be null")).toList();
        if (stages.isEmpty()) throw new IllegalArgumentException("conditions must not be empty");
    }

    @Override public String description() { return "conditions are satisfied in order"; }
    @Override public List<AwaitCondition> stages() { return stages; }
    @Override public ConditionRun<?, ?> newRun(AwaitCondition definition, boolean recording) {
        return new Run<>(definition, recording);
    }

    private final class Run<OBSERVED> extends AbstractConditionRun<OBSERVED, List<OBSERVED>> {

        private final List<ConditionExecution<OBSERVED, Object>> evaluations;
        private final List<OBSERVED> results = new ArrayList<>();
        private int attemptedStage = 1;

        private Run(AwaitCondition definition, boolean recording) {
            super(definition, recording);
            evaluations = stages.stream().map(ConditionExecution::<OBSERVED, Object>bind).toList();
        }

        @Override
        public void beginAttempt() { attemptedStage = min(results.size() + 1, stages.size()); }

        @Override
        public ConditionResult<?> probe(OBSERVED actual) throws Throwable {
            if (complete()) results.removeLast();
            var evaluated = evaluations.get(attemptedStage - 1).probe(actual);
            if (evaluated instanceof Satisfied<?>) results.add(actual);
            return evaluated;
        }

        @Override
        public boolean complete() { return results.size() == stages.size(); }

        @Override
        public List<OBSERVED> result() { return results.stream().toList(); }

        @Override
        protected int historySlot() { return attemptedStage; }

        @Override
        protected List<AwaitCondition> historyConditions() { return stages; }

        @Override
        protected void describeAttempt(DiagnosticText text, StringBuilder out) {
            field(out, "", "Sequence", "captured " + results.size() + "/" + stages.size()
                    + "; stage " + attemptedStage + "/" + stages.size());
            if (condition.explanation() != null) field(out, "Because", condition.explanation());
            out.append('\n');
            text.condition(out, "Condition", stages.get(attemptedStage - 1), "");
        }

        @Override
        protected void describeNext(DiagnosticText text, StringBuilder out, ExecutionStep<?> step) {
            if (step.attempt() instanceof SatisfiedObservation<?> && !complete()) {
                int next = results.size() + 1;
                text.condition(out, "Next stage " + next + "/" + stages.size(), stages.get(next - 1), "");
            }
        }
    }
}
