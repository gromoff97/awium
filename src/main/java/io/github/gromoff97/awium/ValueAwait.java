package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
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

    ValueAwait(Callable<? extends OBSERVED> source) {
        this(requireNonNull(source, "source must not be null"), DEFAULT);
    }

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        this.source = source;
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

    public OBSERVED until(PreservingCondition<? super OBSERVED> condition) {
        return complete(required(condition).preservingAttempt());
    }

    public OBSERVED until(ComparisonCondition<? extends OBSERVED> condition) {
        return complete(required(condition).definition.preservingAttempt());
    }

    public <RESULT> RESULT until(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return complete(required(condition).newAttempt());
    }

    public AwaitResult<OBSERVED, OBSERVED> tryUntil(PreservingCondition<? super OBSERVED> condition) {
        return capture(required(condition).preservingAttempt());
    }

    public AwaitResult<OBSERVED, OBSERVED> tryUntil(ComparisonCondition<? extends OBSERVED> condition) {
        return capture(required(condition).definition.preservingAttempt());
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> tryUntil(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return capture(required(condition).newAttempt());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(PreservingCondition<? super OBSERVED>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(PreservingCondition<? super OBSERVED>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(ComparisonCondition<? extends OBSERVED>... conditions) {
        return until(ConditionSupport.captured(conditions));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(ComparisonCondition<? extends OBSERVED>... conditions) {
        return tryUntil(ConditionSupport.captured(conditions));
    }

    final <RESULT> RESULT complete(ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT> session) {
        return complete(new WaitEngine<>(configuration, source, session).waitFor(),
                session.condition, configuration);
    }

    static <OBSERVED, RESULT> RESULT complete(WaitCompletion<OBSERVED, RESULT> outcome,
            Condition<?, ?> condition, WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return success.result();
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, condition, configuration);
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
                FailureDiagnostics.failure(outcome, session.condition, configuration));
    }
}
