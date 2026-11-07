package io.github.gromoff97.awium.internal;

import io.github.gromoff97.awium.Condition;
import io.github.gromoff97.awium.execution.AwaitHandler;
import io.github.gromoff97.awium.execution.DefaultAwaitHandler;
import io.github.gromoff97.awium.execution.ProbeFailure;
import io.github.gromoff97.awium.result.Attempt;
import io.github.gromoff97.awium.result.AttemptResult;
import io.github.gromoff97.awium.result.AwaitResult;
import io.github.gromoff97.awium.result.ConditionHistory;
import io.github.gromoff97.awium.result.Diagnostic;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static io.github.gromoff97.awium.execution.AwaitHandler.AttemptFinished;
import static io.github.gromoff97.awium.execution.AwaitHandler.AttemptStarted;
import static io.github.gromoff97.awium.execution.AwaitHandler.WaitFinished;
import static io.github.gromoff97.awium.execution.AwaitHandler.WaitStarted;
import static io.github.gromoff97.awium.internal.DiagnosticReport.failure;
import static io.github.gromoff97.awium.internal.ExecutionStep.Phase;
import static io.github.gromoff97.awium.result.Attempt.FailedAfterObservation;
import static io.github.gromoff97.awium.result.Attempt.FailedBeforeObservation;
import static io.github.gromoff97.awium.result.Attempt.Failure;
import static io.github.gromoff97.awium.result.Attempt.Operation;
import static io.github.gromoff97.awium.result.Attempt.SatisfiedObservation;
import static io.github.gromoff97.awium.result.Attempt.UnsatisfiedObservation;
import static io.github.gromoff97.awium.result.AwaitResult.Failed;
import static io.github.gromoff97.awium.result.AwaitResult.Succeeded;
import static java.lang.Thread.currentThread;
import static java.time.Duration.ofNanos;
import static java.util.Objects.requireNonNull;

final class AwaitRun<OBSERVED, RESULT> {

    private final Callable<? extends OBSERVED> source;
    private final Condition<? super OBSERVED, RESULT> definition;
    private final LongSupplier clock;
    private final long started;
    private final GuardedAttempt<OBSERVED, RESULT> evaluation;
    private final DefaultAwaitHandler<OBSERVED> history = new DefaultAwaitHandler<>();
    private final Supplier<? extends AwaitHandler<OBSERVED>> handlerFactory;
    private AwaitHandler<OBSERVED> handler = history;
    private AttemptStarted pendingAttempt;
    private AttemptFinished<OBSERVED> lastEvent;
    private long attemptNumber;
    private long elapsed;
    private Condition<? super OBSERVED, RESULT> condition;
    private RESULT selectedValue;

    AwaitRun(Callable<? extends OBSERVED> source, Condition<? super OBSERVED, RESULT> definition, LongSupplier clock) {
        this(source, definition, clock, null);
    }

    AwaitRun(Callable<? extends OBSERVED> source, Condition<? super OBSERVED, RESULT> definition, LongSupplier clock,
            Supplier<? extends AwaitHandler<OBSERVED>> handlerFactory) {
        this.handlerFactory = handlerFactory;
        this.source = source;
        this.definition = requireNonNull(definition, "condition must not be null");
        this.clock = clock;
        evaluation = new GuardedAttempt<>(() -> condition.newAttempt());
        started = clock.getAsLong();
    }

    long elapsedNanos() {
        elapsed = clock.getAsLong() - started;
        return elapsed;
    }

    ExecutionStep<OBSERVED> initialize() {
        return initialize(WaitConfiguration.DEFAULT);
    }

    ExecutionStep<OBSERVED> initialize(WaitConfiguration configuration) {
        var start = new WaitStarted(definition, ofNanos(configuration.everyNanos()),
                ofNanos(configuration.upToNanos()), ofNanos(configuration.persistenceNanos()));
        history.onStart(start);
        boolean interrupted = currentThread().isInterrupted();
        try {
            if (handlerFactory != null) {
                handler = requireNonNull(handlerFactory.get(), "handler factory returned null");
                handler.onStart(start);
            }
        } catch (Throwable failure) {
            return failed(Phase.ACQUISITION, new FailedBeforeObservation<>(Operation.WAITING, failure));
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        try {
            condition = GuardedAttempt.prepare(definition);
            return null;
        } catch (Throwable failure) {
            return failed(Phase.ACQUISITION,
                    new FailedBeforeObservation<>(Operation.CONDITION, failure));
        }
    }

    private Throwable startAttempt() {
        if (pendingAttempt != null) return null;
        pendingAttempt = new AttemptStarted(attemptNumber + 1, ofNanos(elapsed));
        boolean interrupted = currentThread().isInterrupted();
        try {
            handler.onAttemptStart(pendingAttempt);
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            if (interrupted) currentThread().interrupt();
        }
    }

    ExecutionStep<OBSERVED> poll(Phase phase) {
        var interrupted = interruptedBefore(phase);
        if (interrupted != null) return interrupted;
        var startFailure = startAttempt();
        if (startFailure != null) {
            return failed(phase, new FailedBeforeObservation<>(Operation.WAITING, startFailure));
        }
        interrupted = interruptedBefore(phase);
        if (interrupted != null) return interrupted;
        OBSERVED actual;
        try {
            actual = source.call();
        } catch (Throwable failure) {
            return failed(phase,
                    new FailedBeforeObservation<>(Operation.SOURCE, failure));
        }

        if (currentThread().isInterrupted()) {
            return failed(phase, new FailedAfterObservation<>(actual, Operation.SOURCE,
                    new InterruptedException("caller thread interrupt flag was set"), null));
        }

        Attempt<OBSERVED> attempt;
        Attempt<OBSERVED> rootAttempt = null;
        Diagnostic diagnostic = Diagnostic.EMPTY;
        Throwable conditionCause = null;
        boolean conditionSatisfied = false;
        try {
            var checked = evaluation.probe(actual);
            checked.ifSatisfied(value -> selectedValue = value);
            conditionSatisfied = checked.isSatisfied();
            diagnostic = checked.diagnostic();
            conditionCause = checked.cause();
            rootAttempt = observation(actual, checked);
            var recorded = requireNonNull(condition.historyResult(checked), "condition returned null history result");
            diagnostic = recorded.diagnostic();
            attempt = recorded == checked ? rootAttempt : observation(actual, recorded);
        } catch (ProbeFailure failure) {
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION,
                    failure.getCause(), failure.location(), diagnostic);
        } catch (Throwable failure) {
            attempt = new FailedAfterObservation<>(actual, Operation.CONDITION, failure, null, diagnostic);
        }
        return record(phase, rootAttempt == null ? attempt : rootAttempt, attempt, conditionSatisfied, conditionCause);
    }

    private ExecutionStep<OBSERVED> failed(Phase phase, Failure<OBSERVED> attempt) {
        var startFailure = startAttempt();
        var step = record(phase, attempt, attempt, false, null);
        if (startFailure != null) step = step.withFailure(Operation.WAITING, startFailure, "await handler onAttemptStart");
        return step;
    }

    private Attempt<OBSERVED> observation(OBSERVED actual, AttemptResult<?> result) {
        if (result.isSatisfied()) return new SatisfiedObservation<>(actual, result.diagnostic());
        return new UnsatisfiedObservation<>(actual, result.diagnostic(), result.cause());
    }

    private ExecutionStep<OBSERVED> record(Phase phase, Attempt<OBSERVED> rootAttempt,
            Attempt<OBSERVED> attempt, boolean conditionSatisfied, Throwable conditionCause) {
        Throwable routingFailure = null;
        int slot = 1;
        boolean interrupted = currentThread().isInterrupted();
        try {
            int reportedSlot = condition == null ? 1 : condition.historySlot(attempt);
            if (reportedSlot < 1) throw new IllegalArgumentException("history slot must be positive");
            slot = reportedSlot;
        } catch (Throwable failure) {
            routingFailure = failure;
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        var step = new ExecutionStep<>(phase, elapsedNanos(), attempt, conditionSatisfied)
                .withRelatedCause(conditionCause);
        if (routingFailure != null) step = step.withConditionFailure(routingFailure, "condition history");
        if (step.failureReason() == null && currentThread().isInterrupted()) {
            var cause = conditionCause == null ? step.reportedAttempt().cause() : conditionCause;
            step = step.withConditionFailure(
                    cause == null ? new InterruptedException("caller thread interrupt flag was set") : cause, null);
        }
        attemptNumber++;
        lastEvent = new AttemptFinished<>(attemptNumber, ofNanos(step.completedAfterNanos()),
                rootAttempt, slot, step.reportedAttempt());
        interrupted = currentThread().isInterrupted();
        try {
            handler.onAttemptFinish(lastEvent);
        } catch (Throwable failure) {
            step = step.withFailure(Operation.WAITING, failure, "await handler onAttemptFinish");
        } finally {
            pendingAttempt = null;
            if (interrupted) currentThread().interrupt();
        }
        if (step.failureReason() == null && currentThread().isInterrupted()) {
            step = step.withFailure(Operation.WAITING,
                    new InterruptedException("caller thread interrupt flag was set"), "await handler onAttemptFinish");
        }
        if (handler != history) {
            step = new ExecutionStep<>(phase, elapsedNanos(), step.reportedAttempt(), conditionSatisfied, step.relatedCauses());
        }
        return step;
    }

    ExecutionStep<OBSERVED> interruptedBefore(Phase phase) {
        return currentThread().isInterrupted()
                ? waitingFailure(phase, new InterruptedException("caller thread interrupt flag was set"))
                : null;
    }

    ExecutionStep<OBSERVED> waitingFailure(Phase phase, Throwable failure) {
        return failed(phase,
                new FailedBeforeObservation<>(Operation.WAITING, failure));
    }

    RESULT selectedValue() { return selectedValue; }

    AwaitResult<OBSERVED, RESULT> result(WaitCompletion outcome, WaitConfiguration configuration) {
        return result(outcome, configuration, selectedValue);
    }

    <VALUE> AwaitResult<OBSERVED, VALUE> result(WaitCompletion outcome, WaitConfiguration configuration, VALUE value) {
        Optional<List<Condition<?, ?>>> definitions;
        boolean interrupted = currentThread().isInterrupted();
        try {
            definitions = Optional.of(condition == null ? List.of(definition)
                    : List.copyOf(requireNonNull(condition.historyConditions(definition), "condition returned null history conditions")));
        } catch (Throwable cause) {
            outcome = outcome.withConditionFailure(cause, "condition histories");
            definitions = Optional.empty();
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        List<ConditionHistory<OBSERVED>> conditions;
        interrupted = currentThread().isInterrupted();
        try {
            conditions = List.copyOf(requireNonNull(handler.onFinish(new WaitFinished(
                    ofNanos(outcome.elapsedNanos()), Optional.ofNullable(outcome.failureReason()), definitions)),
                    "handler returned null histories"));
        } catch (Throwable cause) {
            if (handler == history) outcome = outcome.withConditionFailure(cause, "condition histories");
            else outcome = outcome.withFailure(Operation.WAITING, cause, "await handler onFinish");
            conditions = fallback(outcome);
        } finally {
            if (interrupted) currentThread().interrupt();
        }
        if (outcome.failureReason() == null && currentThread().isInterrupted()) {
            outcome = outcome.withFailure(Operation.WAITING,
                    new InterruptedException("caller thread interrupt flag was set"), "await handler onFinish");
        }
        return outcome.failureReason() == null
                ? new Succeeded<>(conditions, value)
                : new Failed<>(conditions,
                        failure(definition, this::diagnostics, outcome, configuration, attemptNumber, conditions));
    }

    private List<ConditionHistory<OBSERVED>> fallback(WaitCompletion outcome) {
        if (handler != history) {
            var attempt = lastEvent.attempt();
            history.onAttemptFinish(new AttemptFinished<>(attemptNumber, ofNanos(outcome.elapsedNanos()),
                    attempt, 1, lastEvent.historyAttempt()));
        }
        return history.onFinish(new WaitFinished(ofNanos(outcome.elapsedNanos()),
                Optional.ofNullable(outcome.failureReason()), Optional.empty()));
    }

    private Diagnostic diagnostics(Attempt<?> attempt) { return condition == null ? Diagnostic.EMPTY : condition.diagnostics(attempt); }
}
