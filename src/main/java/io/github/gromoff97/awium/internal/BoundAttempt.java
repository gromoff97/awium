package io.github.gromoff97.awium.internal;

import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public record BoundAttempt<OBSERVED, RESULT>(OBSERVED observed,
        Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluator)
        implements Callable<AttemptResult<? extends RESULT>> {

    public BoundAttempt {
        requireNonNull(evaluator, "evaluator must not be null");
    }

    @Override
    public AttemptResult<? extends RESULT> call() {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return interrupted(null);
            }
            var check = evaluator.call();
            if (Thread.currentThread().isInterrupted()) {
                return interrupted(null);
            }
            var result = requireNonNull(check.probe(observed), "condition returned null evaluation");
            if (!(result instanceof AttemptResult.Uncontrolled<?>) && Thread.currentThread().isInterrupted()) {
                return interrupted(result.cause());
            }
            return result;
        } catch (Throwable failure) {
            return AttemptResult.uncontrolled(failure);
        }
    }

    private AttemptResult<RESULT> interrupted(Throwable cause) {
        return AttemptResult.uncontrolled(cause == null
                ? new InterruptedException("caller thread interrupt flag was set") : cause);
    }
}
