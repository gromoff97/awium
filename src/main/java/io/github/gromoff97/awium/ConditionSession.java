package io.github.gromoff97.awium;

import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.github.gromoff97.awium.ConditionResult.uncontrolled;
import static java.util.Objects.requireNonNull;

/** One execution of a condition, also used as an ordered sequence stage. */
final class ConditionSession<Observed, Result> implements Function<Observed, ConditionResult<? extends Result>> {

    final ConditionMetadata metadata;
    private final Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory;
    private CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>> evaluator;

    ConditionSession(ConditionMetadata metadata,
            Callable<? extends CheckedFunction<? super Observed, ? extends ConditionResult<? extends Result>>> evaluatorFactory) {
        this.metadata = requireNonNull(metadata, "metadata must not be null");
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    @Override
    public ConditionResult<? extends Result> apply(Observed actual) {
        try {
            if (evaluator == null) {
                evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            }
            return evaluator.apply(actual);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            return uncontrolled(failure);
        }
    }
}
