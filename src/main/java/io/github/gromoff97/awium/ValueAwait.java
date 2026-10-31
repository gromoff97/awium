package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.CapturedSource;
import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.PreservesObservation;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

/**
 * Immutable fluent configuration for a value source. Each terminal call starts a fresh wait;
 * the source and other supplied callbacks remain caller-owned.
 *
 * @param <OBSERVED> complete value returned by the source
 */
public sealed class ValueAwait<OBSERVED> extends AwaitSupport<OBSERVED> permits SelectionAwait {

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    /** Returns a new configuration with a positive delay between attempts. */
    public ValueAwait<OBSERVED> every(Duration interval) {
        return new ValueAwait<>(source, configuration.withEvery(interval));
    }

    /** Returns a new configuration with a positive acquisition timeout. */
    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return new ValueAwait<>(source, configuration.withUpTo(timeout));
    }

    /**
     * Returns a new configuration requiring continued satisfaction after acquisition.
     * Zero disables persistence; negative durations are rejected.
     */
    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return new ValueAwait<>(source, configuration.withPersistence(persistence));
    }

    /**
     * Returns a new configuration using the supplied monotonic nanosecond clock and wait operation.
     * Both operations must share the same time base. Neither is invoked during configuration.
     * The wait receives a duration in nanoseconds and may return early.
     *
     * <p>The clock must return normally: its failures propagate directly. A failure in the wait
     * operation is reported by {@code until} as a waiting failure.
     *
     * @param clock monotonic nanosecond clock
     * @param parker wait operation receiving a positive duration in nanoseconds
     * @return an independent configuration with the same source and durations
     * @throws NullPointerException if either operation is null
     */
    ValueAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            OBSERVED until(CHECK condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }

    public <RESULT> RESULT until(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }

    public List<OBSERVED> until(
            CapturedSource<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return complete(SequenceExecution.<OBSERVED>captured(required(condition), ConditionSupport::observing));
    }
}
