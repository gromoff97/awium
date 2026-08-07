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
        var callbackFailure = new AtomicReference<CallbackFailure>();
        var interruptionFailure = new AtomicReference<InterruptedException>();
        var executionError = new AtomicReference<Error>();
        try {
            spec.factory().untilAsserted(() -> {
                if (hasExternalFailure(
                        callbackFailure, interruptionFailure, executionError)) {
                    return;
                }
                final T actual;
                try {
                    actual = spec.source().get();
                } catch (Throwable failure) {
                    throwClassifiedSourceFailure(
                            failure, interruptionFailure, executionError);
                    return;
                }
                try {
                    var evaluation = terminal.evaluate(actual);
                    finalEvaluation.set(evaluation);
                } catch (CallbackFailure failure) {
                    callbackFailure.compareAndSet(null, failure);
                    throw failure;
                } catch (Throwable failure) {
                    var interruption = findCause(failure, InterruptedException.class);
                    if (interruption != null) {
                        interruptionFailure.compareAndSet(null, interruption);
                        throw new InterruptionSignal(interruption);
                    }
                    if (failure instanceof Error error
                            && !(error instanceof AssertionError)) {
                        executionError.compareAndSet(null, error);
                        throw new ExecutionErrorSignal(error);
                    }
                    throw failure;
                }
            });
        } catch (InterruptionSignal signal) {
            throw propagateInterruption(signal.interruption());
        } catch (ExecutionErrorSignal signal) {
            throw signal.error();
        } catch (ConditionTimeoutException | TerminalFailureException engineFailure) {
            propagateExternalFailure(
                    callbackFailure, interruptionFailure, executionError);
            var interruption = findCause(engineFailure, InterruptedException.class);
            if (interruption != null) {
                throw propagateInterruption(interruption);
            }
            throw Diagnostics.awaitFailure(spec, terminalName, engineFailure);
        } catch (CallbackFailure escapedCallbackFailure) {
            throw escapedCallbackFailure.original();
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

        propagateExternalFailure(callbackFailure, interruptionFailure, executionError);
        var evaluation = finalEvaluation.get();
        if (evaluation == null) {
            throw new IllegalStateException("Awaitility completed without a successful evaluation");
        }
        return evaluation.resolve();
    }

    private static void throwClassifiedSourceFailure(
            Throwable failure,
            AtomicReference<InterruptedException> interruptionFailure,
            AtomicReference<Error> executionError) throws Throwable {
        var interruption = findCause(failure, InterruptedException.class);
        if (interruption != null) {
            interruptionFailure.compareAndSet(null, interruption);
            throw new InterruptionSignal(interruption);
        }
        if (failure instanceof Error error) {
            executionError.compareAndSet(null, error);
            throw new ExecutionErrorSignal(error);
        }
        throw failure;
    }

    private static boolean hasExternalFailure(
            AtomicReference<CallbackFailure> callbackFailure,
            AtomicReference<InterruptedException> interruptionFailure,
            AtomicReference<Error> executionError) {
        return callbackFailure.get() != null
                || interruptionFailure.get() != null
                || executionError.get() != null;
    }

    private static void propagateExternalFailure(
            AtomicReference<CallbackFailure> callbackFailure,
            AtomicReference<InterruptedException> interruptionFailure,
            AtomicReference<Error> executionError) {
        var interruption = interruptionFailure.get();
        if (interruption != null) {
            throw propagateInterruption(interruption);
        }
        var error = executionError.get();
        if (error != null) {
            throw error;
        }
        var callback = callbackFailure.get();
        if (callback != null) {
            throw callback.original();
        }
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

    private static final class ExecutionErrorSignal extends Error {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Error error;

        private ExecutionErrorSignal(Error error) {
            super(error);
            this.error = error;
        }

        private Error error() {
            return error;
        }
    }
}
