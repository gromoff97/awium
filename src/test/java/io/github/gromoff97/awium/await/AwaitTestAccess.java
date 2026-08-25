package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class AwaitTestAccess {

    private AwaitTestAccess() {
        throw new AssertionError("Utility class");
    }

    public static <S> Await<S, S> timedAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new Await<>(source, actual -> actual, configuration, clock, parker);
    }

    public static <E, C extends Collection<E>> Await<C, E> timedCollectionAwait(
            Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, actual -> actual.iterator().next(),
                configuration, clock, parker);
    }

    public static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>> timedMapAwait(
            Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, actual -> actual.entrySet().iterator().next(),
                configuration, clock, parker);
    }
}
