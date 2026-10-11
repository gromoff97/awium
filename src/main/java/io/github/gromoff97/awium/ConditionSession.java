package io.github.gromoff97.awium;

import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.github.gromoff97.awium.ConditionResult.uncontrolled;
import static io.github.gromoff97.awium.ValueMatching.checkInterruption;
import static java.util.Objects.requireNonNull;

/** Lazily executes a condition, converting nonfatal callback failures into uncontrolled results. */
@SuppressWarnings("removal")
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
                checkInterruption();
                evaluator = requireNonNull(evaluatorFactory.call(), "evaluator must not be null");
            }
            checkInterruption();
            var result = evaluator.apply(actual);
            if (result instanceof ConditionResult.Uncontrolled<?> failure) {
                rethrowFatalOrRestoreInterrupt(failure.cause());
            }
            return result;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            rethrowFatalOrRestoreInterrupt(failure);
            return uncontrolled(failure);
        }
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
