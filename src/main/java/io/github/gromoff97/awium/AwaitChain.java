package io.github.gromoff97.awium;

import io.github.gromoff97.awium.internal.diagnostic.FailureFactory;
import io.github.gromoff97.awium.internal.engine.Interrupts;
import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.internal.engine.WaitEngine;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class AwaitChain<S> {

    private final AwaitSources.Source<S> source;
    private final WaitConfiguration config;
    private final LongSupplier clock;
    private final LongConsumer parker;
    private final Interrupts interrupts;
    private final FailureFactory failureFactory;

    AwaitChain(AwaitSources.Source<S> source) {
        this(source, WaitConfiguration.defaults(), System::nanoTime,
                LockSupport::parkNanos, new Interrupts(),
                new FailureFactory());
    }

    AwaitChain(AwaitSources.Source<S> source, WaitConfiguration config,
            LongSupplier clock, LongConsumer parker, Interrupts interrupts,
            FailureFactory failureFactory) {
        this.source = Objects.requireNonNull(source);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.parker = Objects.requireNonNull(parker);
        this.interrupts = Objects.requireNonNull(interrupts);
        this.failureFactory = Objects.requireNonNull(failureFactory);
    }

    AwaitSources.Source<S> source() {
        return source;
    }

    WaitConfiguration config() {
        return config;
    }

    AwaitChain<S> withEvery(Duration interval) {
        return withConfig(config.withEvery(interval));
    }

    AwaitChain<S> withUpTo(Duration timeout) {
        return withConfig(config.withUpTo(timeout));
    }

    AwaitChain<S> withStableFor(Duration stability) {
        return withConfig(config.withStableFor(stability));
    }

    <R> R execute(ConditionRuntime<S, R> condition) {
        WaitEngine engine = new WaitEngine(config, clock, parker, interrupts);
        AttemptEvaluator<S, R> evaluator = new AttemptEvaluator<>(
                source, condition, interrupts);
        return failureFactory.complete(
                engine.waitFor(evaluator::evaluate), condition.description(),
                condition.explanation(), config);
    }

    private AwaitChain<S> withConfig(WaitConfiguration candidate) {
        return new AwaitChain<>(source, candidate, clock, parker,
                interrupts, failureFactory);
    }
}
