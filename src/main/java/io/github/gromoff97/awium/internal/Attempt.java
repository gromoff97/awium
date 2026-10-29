package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

/** An immutable binding of an observation to a check; each call returns a separate evaluation. */
public final class Attempt<OBSERVED, RESULT> implements Callable<AttemptResult<? extends RESULT>> {

    private final Condition.Check condition;
    private final OBSERVED observed;
    private final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluator;

    Attempt(Condition.Check condition, OBSERVED observed,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluator) {
        this.condition = requireNonNull(condition, "condition must not be null");
        this.observed = observed;
        this.evaluator = requireNonNull(evaluator, "evaluator must not be null");
    }

    public Condition.Check condition() {
        return condition;
    }

    public OBSERVED observed() {
        return observed;
    }

    @Override
    public AttemptResult<? extends RESULT> call() {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return interrupted(null, null);
            }
            var check = evaluator.call();
            if (Thread.currentThread().isInterrupted()) {
                return interrupted(null, null);
            }
            var result = requireNonNull(check.probe(observed), "condition returned null evaluation");
            if (!(result instanceof AttemptResult.Uncontrolled<?>) && Thread.currentThread().isInterrupted()) {
                return interrupted(result.status().cause(), result.condition());
            }
            return result;
        } catch (Throwable failure) {
            return AttemptResult.uncontrolled(failure, null);
        }
    }

    private AttemptResult<RESULT> interrupted(Throwable cause, Condition diagnostic) {
        return AttemptResult.uncontrolled(cause == null
                ? new InterruptedException("caller thread interrupt flag was set") : cause, diagnostic);
    }
}
