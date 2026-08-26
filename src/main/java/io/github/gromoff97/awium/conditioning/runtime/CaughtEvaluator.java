package io.github.gromoff97.awium.conditioning.runtime;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
final class CaughtEvaluator<S, R>
        implements Function<S, Evaluation<List<R>>> {

    private final List<Stage<S, R>> stages;
    private final ArrayList<R> results = new ArrayList<>();

    CaughtEvaluator(List<Stage<S, R>> stages) {
        this.stages = List.copyOf(stages);
    }

    @Override
    public Evaluation<List<R>> apply(S actual) {
        boolean complete = results.size() == stages.size();
        int caught = complete ? stages.size() - 1 : results.size();
        Evaluation<? extends R> evaluation;
        try {
            evaluation = stages.get(caught).evaluator().apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return Evaluation.<List<R>>uncontrolled(failure).withContext(context(caught));
        }
        if (evaluation == null) {
            return Evaluation.<List<R>>uncontrolled(new NullPointerException("condition returned null Evaluation")).withContext(context(caught));
        }
        Evaluation<? extends R> contextual = evaluation.withContext(context(caught));
        return complete
                ? contextual.continueIfSatisfied(this::replaceLast)
                : contextual.continueIfSatisfied(result -> capture(result, caught));
    }

    private Evaluation<List<R>> capture(R result, int evaluatedStage) {
        results.add(result);
        if (results.size() < stages.size()) {
            return Evaluation.<List<R>>unsatisfied("waiting for sequence stage " + (results.size() + 1))
                    .withContext(context(results.size(), evaluatedStage));
        }
        return satisfied(resultCopy());
    }

    private Evaluation<List<R>> replaceLast(R result) {
        results.set(results.size() - 1, result);
        return satisfied(resultCopy());
    }

    private List<R> resultCopy() {
        return results.stream().toList();
    }

    private Evaluation.Context.Sequence context(int caught) {
        return context(caught, caught);
    }

    private Evaluation.Context.Sequence context(int caught, int evaluatedStage) {
        Stage<S, R> metadata = stages.get(caught);
        return new Evaluation.Context.Sequence(caught, stages.size(), caught + 1,
                evaluatedStage + 1, metadata.expectation(), metadata.importance());
    }

    record Stage<S, R>(Function<? super S,
            ? extends Evaluation<? extends R>> evaluator,
            String expectation, String importance) {

        public Stage {
            requireNonNull(evaluator, "evaluator must not be null");
            requireNonNull(expectation, "expectation must not be null");
        }
    }
}
