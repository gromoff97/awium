package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

final class AwaitTestAccess {

    private AwaitTestAccess() {}

    static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence,
                WaitConfiguration.DEFAULT.clock(), WaitConfiguration.DEFAULT.parker());
    }

    static <S> ValueAwait<S> timedAwait(Callable<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new ValueAwait<>(source, configuration.withTime(clock, parker));
    }

    static <E, C extends Collection<E>> Await<C, E, Void> timedCollectionAwait(
            Callable<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration.withTime(clock, parker));
    }

    static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>, V> timedMapAwait(
            Callable<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration.withTime(clock, parker));
    }
}
