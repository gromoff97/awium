package io.github.gromoff97.awium;

import io.github.gromoff97.awium.Condition.ComparisonCondition;
import io.github.gromoff97.awium.Condition.PreservingCondition;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static io.github.gromoff97.awium.Condition.required;
import static io.github.gromoff97.awium.WaitConfiguration.DEFAULT;
import static java.util.Objects.requireNonNull;

public sealed class ValueAwait<OBSERVED> permits Await {

    final Source<? extends OBSERVED> source;
    final WaitConfiguration configuration;
    final LongSupplier clock;
    final LongConsumer parker;

    ValueAwait(Source<? extends OBSERVED> source) {
        this(requireNonNull(source, "source must not be null"),
                DEFAULT, System::nanoTime, LockSupport::parkNanos);
    }

    ValueAwait(Source<? extends OBSERVED> source, WaitConfiguration configuration,
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
        return complete(required(condition).preservingSession());
    }

    public OBSERVED until(ComparisonCondition<? extends OBSERVED> condition) {
        return complete(required(condition).definition.preservingSession());
    }

    public <RESULT> RESULT until(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return complete(required(condition).newSession());
    }

    public AwaitResult<OBSERVED, OBSERVED> tryUntil(PreservingCondition<? super OBSERVED> condition) {
        return capture(required(condition).preservingSession());
    }

    public AwaitResult<OBSERVED, OBSERVED> tryUntil(ComparisonCondition<? extends OBSERVED> condition) {
        return capture(required(condition).definition.preservingSession());
    }

    public <RESULT> AwaitResult<OBSERVED, RESULT> tryUntil(Condition<? super OBSERVED, ? extends RESULT> condition) {
        return capture(required(condition).newSession());
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(PreservingCondition<? super OBSERVED> first, PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        return until(CapturedEvaluator.<OBSERVED>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(PreservingCondition<? super OBSERVED> first, PreservingCondition<? super OBSERVED> second, PreservingCondition<? super OBSERVED>... rest) {
        return tryUntil(CapturedEvaluator.<OBSERVED>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final List<OBSERVED> until(ComparisonCondition<? extends OBSERVED> first, ComparisonCondition<? extends OBSERVED> second, ComparisonCondition<? extends OBSERVED>... rest) {
        return until(CapturedEvaluator.<OBSERVED>captured(first, second, rest));
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public final AwaitResult<OBSERVED, List<OBSERVED>> tryUntil(ComparisonCondition<? extends OBSERVED> first, ComparisonCondition<? extends OBSERVED> second, ComparisonCondition<? extends OBSERVED>... rest) {
        return tryUntil(CapturedEvaluator.<OBSERVED>captured(first, second, rest));
    }

    final <RESULT> RESULT complete(ConditionSession<? super OBSERVED, ? extends RESULT> session) {
        return complete(new WaitEngine<OBSERVED, RESULT>(configuration, clock, parker, source, session).waitFor(),
                session.metadata, configuration);
    }

    static <OBSERVED, RESULT> RESULT complete(WaitCompletion<OBSERVED, RESULT> outcome,
            ConditionMetadata metadata, WaitConfiguration configuration) {
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return success.result();
        }
        AwaitFailure failure = FailureDiagnostics.failure(outcome, metadata, configuration);
        switch (failure.reason()) {
            case TIMEOUT, PERSISTENCE_FAILED -> throw new AwaitAssertionError(failure);
            default -> throw new AwaitExecutionException(failure);
        }
    }

    final <RESULT> AwaitResult<OBSERVED, RESULT> capture(ConditionSession<? super OBSERVED, ? extends RESULT> session) {
        var history = new AttemptHistory<OBSERVED, RESULT>();
        var outcome = new WaitEngine<OBSERVED, RESULT>(configuration, clock, parker, source, session).waitFor(history);
        if (outcome instanceof WaitCompletion.Satisfied<OBSERVED, RESULT> success) {
            return new AwaitResult.Satisfied<>(history.snapshot(), success.attempt().number(), success.result());
        }
        return new AwaitResult.Failed<>(history.snapshot(), outcome.attempt().number(),
                FailureDiagnostics.failure(outcome, session.metadata, configuration));
    }
}
