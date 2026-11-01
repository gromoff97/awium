package io.github.gromoff97.awium.conditions.common;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.ConditionExecution;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.ArrayList;
import java.util.List;

import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Operation.CONDITION;
import static io.github.gromoff97.awium.result.AttemptResult.Satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.satisfied;
import static io.github.gromoff97.awium.result.AttemptResult.unsatisfied;
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
    @Override public Condition<?, ?> newRun() {
        return new Run<>();
    }

    private final class Run<OBSERVED> implements Condition<OBSERVED, List<OBSERVED>> {

        private final List<ConditionExecution<OBSERVED, Object>> evaluations;
        private final List<OBSERVED> results = new ArrayList<>();
        private int attemptedStage = 1;
        private AttemptResult<?> stageResult;

        private Run() {
            evaluations = stages.stream().map(ConditionExecution::<OBSERVED, Object>bind).toList();
        }

        @Override
        public String description() { return CapturedCondition.this.description(); }

        @Override
        public AttemptResult<List<OBSERVED>> probe(OBSERVED actual) throws Throwable {
            attemptedStage = min(results.size() + 1, stages.size());
            if (results.size() == stages.size()) results.removeLast();
            stageResult = evaluations.get(attemptedStage - 1).probe(actual);
            if (stageResult instanceof Satisfied<?>) results.add(actual);
            var diagnostic = stageResult.diagnostic();
            if (results.size() == stages.size()) return satisfied(results.stream().toList(), diagnostic);
            var mismatch = diagnostic.mismatch();
            return unsatisfied(mismatch == null || mismatch.isBlank()
                    ? diagnostic.withMismatch("condition is not yet complete") : diagnostic, stageResult.cause());
        }

        @Override
        public AttemptResult<?> historyResult(AttemptResult<? extends List<OBSERVED>> attempt) { return stageResult; }

        @Override
        public int historySlot(Attempt<?> attempt) {
            return attempt instanceof FailedBeforeObservation<?>
                    || attempt instanceof Failure<?> failure && failure.operation() != CONDITION
                    ? min(results.size() + 1, stages.size()) : attemptedStage;
        }

        @Override
        public List<AwaitCondition> historyConditions(AwaitCondition definition) { return stages; }

        @Override
        public Diagnostic diagnostics(Attempt<?> attempt) {
            int stage = historySlot(attempt);
            var fields = new ArrayList<Reference<?>>();
            fields.add(new Reference<>("Sequence", "captured " + results.size() + "/" + stages.size()
                    + "; stage " + stage + "/" + stages.size()));
            fields.add(new Reference<>("Current stage", stages.get(stage - 1)));
            if (attempt instanceof SatisfiedObservation<?> && results.size() != stages.size()) {
                int next = results.size() + 1;
                fields.add(new Reference<>("Next stage " + next + "/" + stages.size(), stages.get(next - 1)));
            }
            return new Diagnostic(null, fields);
        }
    }
}
