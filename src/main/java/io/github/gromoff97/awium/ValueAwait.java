package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.util.Objects.requireNonNull;

public sealed class ValueAwait<OBSERVED> permits Await {

    private static final int MAX_RETAINED_ATTEMPTS = 256;

    final Callable<? extends OBSERVED> source;
    final WaitConfiguration configuration;
    final LongSupplier clock;
    final LongConsumer parker;

    ValueAwait(Callable<? extends OBSERVED> source) {
        this(requireNonNull(source, "source must not be null"),
                DEFAULT, System::nanoTime, LockSupport::parkNanos);
    }

    ValueAwait(Callable<? extends OBSERVED> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        this.source = source;
        this.configuration = configuration;
        this.clock = clock;
        this.parker = parker;
    }

    public ValueAwait<OBSERVED> every(Duration interval) {
        return reconfigured(configuration.withEvery(interval));
    }

    public ValueAwait<OBSERVED> upTo(Duration timeout) {
        return reconfigured(configuration.withUpTo(timeout));
    }

    public ValueAwait<OBSERVED> persisting(Duration persistence) {
        return reconfigured(configuration.withPersistence(persistence));
    }

    public ValueAwait<OBSERVED> usingTime(LongSupplier clock, LongConsumer parker) {
        return new ValueAwait<>(source, configuration,
                requireNonNull(clock, "clock must not be null"),
                requireNonNull(parker, "parker must not be null"));
    }

    private ValueAwait<OBSERVED> reconfigured(WaitConfiguration configuration) {
        return new ValueAwait<>(source, configuration, clock, parker);
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
    public final List<OBSERVED> until(PreservingCondition<? super OBSERVED> first, PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        return until(ConditionSupport.captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(PreservingCondition<? super OBSERVED> first, PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        return tryUntil(ConditionSupport.captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(ComparisonCondition<? extends OBSERVED> first, ComparisonCondition<? extends OBSERVED> second, ComparisonCondition<? extends OBSERVED>... rest) {
        return until(ConditionSupport.captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(ComparisonCondition<? extends OBSERVED> first, ComparisonCondition<? extends OBSERVED> second, ComparisonCondition<? extends OBSERVED>... rest) {
        return tryUntil(ConditionSupport.captured(first, second, rest));
    }

    final <RESULT> RESULT complete(ConditionAwaitAttempt<? super OBSERVED, ? extends RESULT> session) {
        return complete(new WaitEngine<OBSERVED, RESULT>(configuration, clock, parker, source, session).waitFor(),
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
        var outcome = new WaitEngine<OBSERVED, RESULT>(configuration, clock, parker, source, session).waitFor(attempt -> {
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
