package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.Condition.SourceCondition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static java.util.Objects.requireNonNull;

/**
 * Immutable fluent configuration for a value source. Each terminal call starts a fresh wait;
 * the source and other supplied callbacks remain caller-owned.
 *
 * @param <OBSERVED> complete value returned by the source
 */
public sealed class ValueAwait<OBSERVED> permits Await {

    private static final int MAX_RETAINED_ATTEMPTS = 256;

    final Callable<? extends OBSERVED> source;
    final WaitConfiguration configuration;

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this.source = requireNonNull(source, "source must not be null");
        this.configuration = configuration;
    }

    /** Returns a new configuration with a positive delay between attempts. */
    public ValueAwait<OBSERVED> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    /** Returns a new configuration with a positive acquisition timeout. */
    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    /**
     * Returns a new configuration requiring continued satisfaction after acquisition.
     * Zero disables persistence; negative durations are rejected.
     */
    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    /**
     * Returns a new configuration using the supplied monotonic nanosecond clock and wait operation.
     * Both operations must share the same time base. Neither is invoked during configuration.
     * The wait receives a duration in nanoseconds and may return early.
     *
     * <p>The clock must return normally: its failures propagate directly. A failure in the wait
     * operation is reported by {@code until} or {@code tryUntil} as a waiting failure.
     *
     * @param clock monotonic nanosecond clock
     * @param parker wait operation receiving a positive duration in nanoseconds
     * @return an independent configuration with the same source and durations
     * @throws NullPointerException if either operation is null
     */
    public ValueAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return reconfigured(configuration.withTime(clock, parker));
    }

    private ValueAwait<OBSERVED> reconfigured(WaitConfiguration configuration) {
        return new ValueAwait<>(source, configuration);
    }

    public OBSERVED until(SourceCondition<? super OBSERVED, ? super Reference<? super OBSERVED>, ?> condition) {
        return complete(ConditionAwaitAttempt.bind(required(condition)));
    }

    public <RESULT> RESULT until(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return complete(new ConditionAwaitAttempt<>(required(condition)));
    }

    public AwaitResult<OBSERVED, OBSERVED> tryUntil(SourceCondition<? super OBSERVED, ? super Reference<? super OBSERVED>, ?> condition) {
        return capture(ConditionAwaitAttempt.bind(required(condition)));
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> tryUntil(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return capture(new ConditionAwaitAttempt<>(required(condition)));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(SourceCondition<? super OBSERVED, ? super Reference<? super OBSERVED>, ?>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(SourceCondition<? super OBSERVED, ? super Reference<? super OBSERVED>, ?>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    /** Captures stages with the same declared result type, without widening it to a common supertype. */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <RESULT> List<RESULT> until(Condition.Typed<? super OBSERVED, RESULT>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final <RESULT> AwaitResult<OBSERVED, List<RESULT>> tryUntil(Condition.Typed<? super OBSERVED, RESULT>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    final <RESULT> RESULT complete(ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT> session) {
        return complete(new WaitEngine<>(configuration, source, session).waitFor(), configuration);
    }

    static <OBSERVED, RESULT> RESULT complete(WaitCompletion<OBSERVED, RESULT> outcome,
            WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return success.result();
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, configuration);
        switch (failure.reason()) {
            case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
            default -> throw new AwaitExecutionException(failure);
        }
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> capture(ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT> session) {
        var attempts = new ArrayList<AttemptRecord<OBSERVED, RESULT>>();
        var outcome = new WaitEngine<OBSERVED, RESULT>(configuration, source, session).waitFor(attempt -> {
            if (attempts.size() == MAX_RETAINED_ATTEMPTS) {
                attempts.remove(1);
            }
            attempts.add(attempt);
        });
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return new AwaitResult.Satisfied<>(attempts, success.result());
        }
        return new AwaitResult.Failed<>(attempts,
                FailureDiagnostics.failure(outcome, configuration));
    }
}
