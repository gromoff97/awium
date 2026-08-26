package io.github.gromoff97.awium.conditioning.conditions;

import io.github.gromoff97.awium.conditioning.Evaluation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static io.github.gromoff97.awium.conditioning.Evaluation.satisfied;
import static io.github.gromoff97.awium.conditioning.Evaluation.unsatisfied;

public final class CaughtEvaluator<S, R>
        implements Function<S, Evaluation<List<R>>> {

    private final List<Function<? super S,
            ? extends Evaluation<? extends R>>> stages;
    private final ArrayList<R> results = new ArrayList<>();
    private int next;

    public CaughtEvaluator(List<? extends Function<? super S,
            ? extends Evaluation<? extends R>>> stages) {
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    @Override
    public Evaluation<List<R>> apply(S actual) {
        Evaluation<? extends R> evaluation = stages.get(next).apply(actual);
        return evaluation == null ? null : evaluation.continueIfSatisfied(this::capture);
    }

    private Evaluation<List<R>> capture(R result) {
        results.add(result);
        if (++next < stages.size()) {
            return unsatisfied("waiting for sequence stage " + (next + 1));
        }
        return satisfied(resultCopy());
    }

    private List<R> resultCopy() {
        return Collections.unmodifiableList(new ArrayList<>(results));
    }
}
