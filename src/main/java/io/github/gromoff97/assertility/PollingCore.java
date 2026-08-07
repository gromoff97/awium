package io.github.gromoff97.assertility;

import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

import java.util.concurrent.atomic.AtomicReference;

final class PollingCore {
    private PollingCore() {
    }

    static <T, R> R await(AwaitSpec<T> spec, String terminalName, Terminal<T, R> terminal) {
        var finalEvaluation = new AtomicReference<Evaluation<R>>();
        try {
            spec.factory().untilAsserted(() -> {
                var actual = spec.source().get();
                var evaluation = terminal.evaluate(actual);
                finalEvaluation.set(evaluation);
            });
        } catch (ConditionTimeoutException | TerminalFailureException engineFailure) {
            var callbackFailure = findCause(engineFailure, CallbackFailure.class);
            if (callbackFailure != null) {
                throw callbackFailure.original();
            }
            throw Diagnostics.awaitFailure(spec, terminalName, engineFailure);
        } catch (CallbackFailure callbackFailure) {
            throw callbackFailure.original();
        } catch (RuntimeException runtimeException) {
            throw runtimeException;
        } catch (Exception checkedException) {
            throw new AwaitExecutionException(checkedException);
        }

        var evaluation = finalEvaluation.get();
        if (evaluation == null) {
            throw new IllegalStateException("Awaitility completed without a successful evaluation");
        }
        return evaluation.resolve();
    }

    static <T, R> AwaitResult<R> tryAwait(
            AwaitSpec<T> spec, String terminalName, Terminal<T, R> terminal) {
        try {
            return AwaitResult.success(await(spec, terminalName, terminal));
        } catch (AwaitFailure failure) {
            return AwaitResult.failed(failure);
        }
    }

    private static <X extends Throwable> X findCause(Throwable failure, Class<X> type) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }
}
