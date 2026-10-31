package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public final class AttemptFactory<OBSERVED, RESULT> {

    final Condition.Check condition;
    private final Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    public AttemptFactory(Condition.Check condition, Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        this.condition = requireNonNull(condition, "condition must not be null");
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    public static <OBSERVED> AttemptFactory<OBSERVED, OBSERVED> observing(
            Condition.Check condition, Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) {
        requireNonNull(evaluatorFactory, "evaluator factory must not be null");
        return new AttemptFactory<>(condition, () -> {
            AwaitAttempt<? super OBSERVED, ?> evaluator = createEvaluator(evaluatorFactory);
            return (AwaitAttempt<OBSERVED, OBSERVED>) actual ->
                    requireNonNull(evaluator.probe(actual), "condition returned null evaluation")
                            .mapSatisfied(ignored -> actual);
        });
    }

    @SuppressWarnings("unchecked")
    private static <OBSERVED, RESULT> AwaitAttempt<? super OBSERVED, ? extends RESULT> createEvaluator(
            Callable<? extends AwaitAttempt<?, ?>> evaluatorFactory) throws Exception {
        // Typed constructors validate factories; closed source/selection families are bound by their terminal constraints.
        return (AwaitAttempt<? super OBSERVED, ? extends RESULT>) evaluatorFactory.call();
    }

    public BoundAttempt<OBSERVED, RESULT> create(OBSERVED actual) {
        return new BoundAttempt<>(actual, this::evaluator);
    }

    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator() throws Exception {
        if (evaluator == null) evaluator = createEvaluator(evaluatorFactory);
        return evaluator;
    }
}
