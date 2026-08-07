package io.github.gromoff97.assertility;

import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.TerminalFailureException;

import java.io.Serial;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicReference;

final class PollingCore {
    private PollingCore() {
    }

    static <T, R> R await(AwaitSpec<T> spec, String terminalName, Terminal<T, R> terminal) {
        var finalEvaluation = new AtomicReference<Evaluation<R>>();
        try {
            spec.factory().untilAsserted(() -> {
                try {
                    var actual = spec.source().get();
                    var evaluation = terminal.evaluate(actual);
                    finalEvaluation.set(evaluation);
                } catch (Throwable failure) {
                    var interruption = findCause(failure, InterruptedException.class);
                    if (interruption != null) {
                        throw new InterruptionSignal(interruption);
                    }
                    throw failure;
                }
            });
        } catch (InterruptionSignal signal) {
            throw propagateInterruption(signal.interruption());
        } catch (ConditionTimeoutException | TerminalFailureException engineFailure) {
            var interruption = findCause(engineFailure, InterruptedException.class);
            if (interruption != null) {
                throw propagateInterruption(interruption);
            }
            throw Diagnostics.awaitFailure(spec, terminalName, engineFailure);
        } catch (CallbackFailure callbackFailure) {
            throw callbackFailure.original();
        } catch (Error error) {
            throw error;
        } catch (RuntimeException runtimeException) {
            var interruption = findCause(runtimeException, InterruptedException.class);
            if (interruption != null) {
                if (runtimeException instanceof AwaitExecutionException awaitExecutionException) {
                    Thread.currentThread().interrupt();
                    throw awaitExecutionException;
                }
                throw propagateInterruption(interruption);
            }
            throw runtimeException;
        } catch (Exception checkedException) {
            if (checkedException instanceof InterruptedException interruption) {
                throw propagateInterruption(interruption);
            }
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
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (var current = failure; current != null && seen.add(current);
                current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        return null;
    }

    private static AwaitExecutionException propagateInterruption(
            InterruptedException interruption) {
        Thread.currentThread().interrupt();
        return new AwaitExecutionException(interruption);
    }

    private static final class InterruptionSignal extends Error {
        @Serial
        private static final long serialVersionUID = 1L;

        private final InterruptedException interruption;

        private InterruptionSignal(InterruptedException interruption) {
            super(interruption);
            this.interruption = interruption;
        }

        private InterruptedException interruption() {
            return interruption;
        }
    }
}
