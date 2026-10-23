package io.github.gromoff97.awium;

import java.util.concurrent.Callable;

import static io.github.gromoff97.awium.CheckedCalls.checkInterruption;
import static java.util.Objects.requireNonNull;

/** Lazily executes a condition, converting nonfatal callback failures into uncontrolled results. */
@SuppressWarnings("removal")
final class ConditionAwaitAttempt<OBSERVED, RESULT> implements AwaitAttempt<OBSERVED, RESULT> {

    final Condition<?, ?> condition;
    private final Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory;
    private AwaitAttempt<? super OBSERVED, ? extends RESULT> evaluator;

    ConditionAwaitAttempt(Condition<?, ?> condition,
            Callable<? extends AwaitAttempt<? super OBSERVED, ? extends RESULT>> evaluatorFactory) {
        this.condition = condition;
        this.evaluatorFactory = evaluatorFactory;
    }

    @Override
    public AttemptResult<? extends RESULT> probe(OBSERVED actual) {
        try {
            if (evaluator == null) {
                checkInterruption();
                evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            }
            checkInterruption();
            var result = requireNonNull(evaluator.probe(actual), "condition returned null ConditionResult");
            if (result instanceof AttemptResult.Uncontrolled<?> failure) {
                rethrowFatalOrRestoreInterrupt(failure.cause());
            }
            return withInterruption(result);
        } catch (Throwable failure) {
            rethrowFatalOrRestoreInterrupt(failure);
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

    private static void rethrowFatalOrRestoreInterrupt(Throwable failure) {
        if (failure instanceof Error fatal
                && (fatal instanceof VirtualMachineError || fatal instanceof ThreadDeath)) {
            throw fatal;
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
