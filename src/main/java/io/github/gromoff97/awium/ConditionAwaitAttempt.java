package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

/** Creates typed attempts and executes them lazily, converting thrown failures into uncontrolled results. */
final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition<?, ?> condition;
    private final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    ConditionAwaitAttempt(Condition<? super OBSERVED, ? extends RESULT> condition) {
        this(condition, condition.evaluatorFactory);
    }

    static <OBSERVED> ConditionAwaitAttempt<OBSERVED, OBSERVED> preserving(Condition<? super OBSERVED, ?> condition) {
        return new ConditionAwaitAttempt<>(condition, () -> {
            var evaluator = condition.evaluatorFactory.call();
            return actual -> {
                var evaluation = evaluator.probe(actual);
                return switch (evaluation) {
                    case AttemptResult.Evaluated<?> value -> new AttemptResult.Evaluated<>(
                            value.result().mapSatisfied(ignored -> actual), value.condition(), value.progress());
                    case AttemptResult.Uncontrolled<?> failure -> new AttemptResult.Uncontrolled<OBSERVED>(
                            failure.cause(), failure.condition(), failure.progress());
                };
            };
        });
    }

    // Only library-created selectors use this bridge; Await binds their result to the source type.
    @SuppressWarnings("unchecked")
    static <OBSERVED, RESULT> ConditionAwaitAttempt<OBSERVED, RESULT> selecting(Condition<? super OBSERVED, ?> condition) {
        return (ConditionAwaitAttempt<OBSERVED, RESULT>) new ConditionAwaitAttempt<>(condition);
    }

    private ConditionAwaitAttempt(Condition<?, ?> condition,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.condition = condition;
        this.evaluatorFactory = evaluatorFactory;
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return new AttemptResult.Uncontrolled<>(
                        new InterruptedException("caller thread interrupt flag was set"), null, null);
            }
            if (evaluator == null) {
                evaluator = evaluatorFactory.call();
                if (Thread.currentThread().isInterrupted()) {
                    return new AttemptResult.Uncontrolled<>(
                            new InterruptedException("caller thread interrupt flag was set"), null, null);
                }
            }
            return withInterruption(evaluator.probe(actual));
        } catch (Throwable failure) {
            return new AttemptResult.Uncontrolled<>(failure, null, null);
        }
    }

    static <RESULT> AttemptResult<? extends RESULT> withInterruption(AttemptResult<? extends RESULT> result) {
        if (!(result instanceof AttemptResult.Uncontrolled<?>) && Thread.currentThread().isInterrupted()) {
            var interruption = new InterruptedException("caller thread interrupt flag was set");
            return new AttemptResult.Uncontrolled<>(interruption, result.condition(), result.progress());
        }
        return result;
    }
}
