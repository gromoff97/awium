package io.github.gromoff97.assertility;

import java.time.Duration;
import java.util.Objects;

final class AwaitChain<S> {

    private final AwaitSources.Source<S> source;
    private final WaitConfig config;
    private final NanoClock clock;
    private final Parker parker;
    private final InterruptGuard interruptGuard;
    private final FailureFactory failureFactory;

    AwaitChain(AwaitSources.Source<S> source) {
        this(source, WaitConfig.defaults(), JdkTime.CLOCK, JdkTime.PARKER,
                new InterruptGuard(), new FailureFactory());
    }

    AwaitChain(AwaitSources.Source<S> source, WaitConfig config,
            NanoClock clock, Parker parker, InterruptGuard interruptGuard,
            FailureFactory failureFactory) {
        this.source = Objects.requireNonNull(source);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
        this.parker = Objects.requireNonNull(parker);
        this.interruptGuard = Objects.requireNonNull(interruptGuard);
        this.failureFactory = Objects.requireNonNull(failureFactory);
    }

    AwaitSources.Source<S> source() {
        return source;
    }

    WaitConfig config() {
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
        WaitEngine engine = new WaitEngine(config, clock, parker, interruptGuard);
        ObservationEvaluator<S, R> evaluator = new ObservationEvaluator<>(
                source, condition, interruptGuard);
        return failureFactory.complete(engine.waitFor(evaluator), condition, config);
    }

    private AwaitChain<S> withConfig(WaitConfig candidate) {
        return new AwaitChain<>(source, candidate, clock, parker,
                interruptGuard, failureFactory);
    }
}
