package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.concurrent.Callable;

import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

public final class GuardedAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    public static <INPUT, RESULT> AwaitAttempt<INPUT, RESULT> bind(Condition<? super INPUT, ? extends RESULT> condition) {
        requireNonNull(condition, "condition must not be null");
        return new GuardedAttempt<>(() -> {
            var check = GuardedAttempt.prepare(condition);
            if (currentThread().isInterrupted()) throw new InterruptedException("caller thread interrupt flag was set");
            return check.newAttempt();
        });
    }

    static <INPUT, RESULT> Condition<? super INPUT, RESULT> prepare(Condition<? super INPUT, RESULT> condition) {
        boolean interrupted = currentThread().isInterrupted();
        try {
            return requireNonNull(condition.newRun(), "condition returned null run");
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    private final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    GuardedAttempt(Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        if (currentThread().isInterrupted()) throw interrupted(null);
        if (evaluator == null) evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
        if (currentThread().isInterrupted()) throw interrupted(null);
        var result = requireNonNull(evaluator.probe(actual), "condition returned null AttemptResult");
        if (currentThread().isInterrupted()) throw interrupted(result.cause());
        return result;
    }

    private static Throwable interrupted(Throwable cause) {
        return cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause;
    }
}
