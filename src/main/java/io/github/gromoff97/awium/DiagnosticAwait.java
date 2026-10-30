package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.CapturedSource;
import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.PreservesObservation;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;

/**
 * Immutable diagnostic configuration returning a result with attempt history. Each terminal call starts a fresh wait;
 * the source and other supplied callbacks remain caller-owned.
 *
 * @param <OBSERVED> complete value returned by the source
 */
public sealed class DiagnosticAwait<OBSERVED> extends AwaitSupport<OBSERVED> permits DiagnosticSelectionAwait {

    DiagnosticAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration) {
        super(source, configuration);
    }

    /** Returns a new configuration with a positive delay between attempts. */
    public DiagnosticAwait<OBSERVED> every(Duration interval) {
        return new DiagnosticAwait<>(source, configuration.withEvery(interval));
    }

    /** Returns a new configuration with a positive acquisition timeout. */
    public DiagnosticAwait<OBSERVED> upTo(Duration timeout) {
        return new DiagnosticAwait<>(source, configuration.withUpTo(timeout));
    }

    /**
     * Returns a new configuration requiring continued satisfaction after acquisition.
     * Zero disables persistence; negative durations are rejected.
     */
    public DiagnosticAwait<OBSERVED> persisting(Duration persistence) {
        return new DiagnosticAwait<>(source, configuration.withPersistence(persistence));
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
    public DiagnosticAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return new DiagnosticAwait<>(source, configuration.withTime(clock, parker));
    }

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            AwaitResult<OBSERVED, OBSERVED> until(CHECK condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> until(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

    public AwaitResult<OBSERVED, List<OBSERVED>> until(
            CapturedSource<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return capture(SequenceExecution.<OBSERVED>captured(required(condition), ConditionSupport::observing));
    }
}
