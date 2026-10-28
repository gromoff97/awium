package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.CapturedSource;
import io.github.gromoff97.awium.Condition.ApplicableCondition;
import io.github.gromoff97.awium.Condition.PreservesObservation;
import io.github.gromoff97.awium.Condition.Reference;
import io.github.gromoff97.awium.internal.ConditionAwaitAttempt;
import io.github.gromoff97.awium.internal.FailureDiagnostics;
import io.github.gromoff97.awium.internal.SequenceExecution;
import io.github.gromoff97.awium.internal.WaitCompletion;
import io.github.gromoff97.awium.internal.WaitConfiguration;
import io.github.gromoff97.awium.internal.WaitEngine;
import io.github.gromoff97.awium.result.AttemptRecord;
import io.github.gromoff97.awium.result.AwaitFailure;
import io.github.gromoff97.awium.result.AwaitResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
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
public sealed class ValueAwait<OBSERVED> permits SelectionAwait {

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

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            OBSERVED until(CHECK condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }

    public <RESULT> RESULT until(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return complete(ConditionSupport.bind(required(condition)));
    }

    public <CHECK extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>> & PreservesObservation>
            AwaitResult<OBSERVED, OBSERVED> tryUntil(CHECK condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> tryUntil(Condition.Typed<? super OBSERVED, ? extends RESULT> condition) {
        return capture(ConditionSupport.bind(required(condition)));
    }

    public List<OBSERVED> until(
            CapturedSource<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return complete(SequenceExecution.<OBSERVED>captured(required(condition), ConditionSupport::observing));
    }

    public AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(
            CapturedSource<? extends ApplicableCondition<? super OBSERVED, ? super Reference<? super OBSERVED>>> condition) {
        return capture(SequenceExecution.<OBSERVED>captured(required(condition), ConditionSupport::observing));
    }

    final <RESULT> RESULT complete(ConditionAwaitAttempt<OBSERVED, RESULT> attempt) {
        return complete(new SequenceExecution<>(attempt)).getFirst();
    }

    private <VALUE> List<VALUE> complete(SequenceExecution<OBSERVED, VALUE> execution) {
        complete(new WaitEngine<>(configuration, source, execution).waitFor(), configuration);
        return execution.values();
    }

    static void complete(WaitCompletion outcome,
            WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied) {
            return;
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, configuration);
        new AwaitResult.Failed<>(List.of(), failure).orThrow();
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> capture(ConditionAwaitAttempt<OBSERVED, RESULT> attempt) {
        return capture(new SequenceExecution<>(attempt), List::getFirst);
    }

    private AwaitResult<OBSERVED, List<OBSERVED>> capture(SequenceExecution<OBSERVED, OBSERVED> execution) {
        return capture(execution, Function.identity());
    }

    private <VALUE, RESULT> AwaitResult<OBSERVED, RESULT> capture(
            SequenceExecution<OBSERVED, VALUE> execution, Function<List<VALUE>, RESULT> result) {
        var attempts = new ArrayList<AttemptRecord<OBSERVED>>();
        var outcome = new WaitEngine<>(configuration, source, execution).waitFor(attempt -> {
            if (attempts.size() == MAX_RETAINED_ATTEMPTS) {
                attempts.remove(1);
            }
            attempts.add(attempt);
        });
        if (outcome instanceof WaitCompletion.Satisfied) {
            return new AwaitResult.Satisfied<>(attempts, result.apply(execution.values()));
        }
        return new AwaitResult.Failed<>(attempts,
                FailureDiagnostics.failure(outcome, configuration));
    }
}
