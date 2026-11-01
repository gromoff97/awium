package io.github.gromoff97.awium.execution;

import io.github.gromoff97.awium.AwaitCondition;
import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.function.AwaitAttempt;
import io.github.gromoff97.awium.result.AttemptResult;
import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.execution.ProbeFailure.locatedFailure;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

public final class ConditionExecution<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    public static <INPUT, RESULT> ConditionExecution<INPUT, RESULT> bind(
            Condition<? super INPUT, ? extends RESULT> condition) {
        return bindRun(condition);
    }

    public static <INPUT, RESULT> ConditionExecution<INPUT, RESULT> bind(AwaitCondition condition) {
        return bindRun(condition);
    }

    private static <INPUT, RESULT> ConditionExecution<INPUT, RESULT> bindRun(AwaitCondition condition) {
        requireNonNull(condition, "condition must not be null");
        return new ConditionExecution<>(() -> {
            var check = ConditionExecution.<INPUT, RESULT>prepare(condition);
            if (currentThread().isInterrupted()) throw new InterruptedException("caller thread interrupt flag was set");
            return check.newAttempt();
        });
    }

    @SuppressWarnings("unchecked")
    static <INPUT, RESULT> Condition<INPUT, RESULT> prepare(AwaitCondition condition) {
        // Terminal bounds determine the result of source-dependent selectors and preserving checks.
        return (Condition<INPUT, RESULT>) requireNonNull(
                requireNonNull(condition, "condition must not be null").newRun(), "condition returned null run");
    }

    private final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    public ConditionExecution(Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.evaluatorFactory = requireNonNull(evaluatorFactory, "evaluator factory must not be null");
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) throws Throwable {
        if (currentThread().isInterrupted()) throw interrupted(null);
        if (evaluator == null) evaluator = evaluatorFactory.call();
        if (currentThread().isInterrupted()) throw interrupted(null);
        var result = requireNonNull(evaluator.probe(actual), "condition returned null AttemptResult");
        if (currentThread().isInterrupted()) throw interrupted(result.cause());
        return result;
    }

    public AttemptResult<? extends RESULT> probeAt(OBSERVED actual, String location) throws Throwable {
        try {
            return probe(actual);
        } catch (Throwable failure) {
            throw locatedFailure(location, failure);
        }
    }

    private static Throwable interrupted(Throwable cause) {
        return cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause;
    }
}
