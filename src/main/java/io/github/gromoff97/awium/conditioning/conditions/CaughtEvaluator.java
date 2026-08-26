package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("removal")
public final class CaughtEvaluator<S, R>
        implements Function<S, Evaluation<List<R>>> {

    private final List<Stage<S, R>> stages;
    private final ArrayList<R> results = new ArrayList<>();
    private int next;

    public CaughtEvaluator(List<Stage<S, R>> stages) {
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    @Override
    public Evaluation<List<R>> apply(S actual) {
        int current = Math.min(next, stages.size() - 1);
        Evaluation<? extends R> evaluation;
        try {
            evaluation = stages.get(current).evaluator().apply(actual);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return Evaluation.<List<R>>uncontrolled(failure).withContext(context(current));
        }
        if (evaluation == null) {
            return Evaluation.<List<R>>uncontrolled(new NullPointerException(
                    "condition returned null Evaluation")).withContext(context(current));
        }
        Evaluation<? extends R> contextual = evaluation.withContext(context(current));
        return next == stages.size()
                ? contextual.continueIfSatisfied(this::replaceLast)
                : contextual.continueIfSatisfied(this::capture);
    }

    private Evaluation<List<R>> capture(R result) {
        results.add(result);
        if (++next < stages.size()) {
            return Evaluation.<List<R>>unsatisfied(
                    "waiting for sequence stage " + (next + 1)).withContext(context(next));
        }
        return satisfied(resultCopy());
    }

    private Evaluation<List<R>> replaceLast(R result) {
        results.set(results.size() - 1, result);
        return satisfied(resultCopy());
    }

    private List<R> resultCopy() {
        return Collections.unmodifiableList(new ArrayList<>(results));
    }

    private Evaluation.Context.Sequence context(int stage) {
        Stage<S, R> metadata = stages.get(stage);
        return new Evaluation.Context.Sequence(stage, stages.size(), stage + 1,
                metadata.expectation(), metadata.importance());
    }

    public record Stage<S, R>(Function<? super S,
            ? extends Evaluation<? extends R>> evaluator,
            String expectation, String importance) {

        public Stage {
            requireNonNull(evaluator, "evaluator must not be null");
            requireNonNull(expectation, "expectation must not be null");
        }
    }
}
