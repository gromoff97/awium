package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.time.Duration.ofNanos;

final class AwaitTestAccess {

    private AwaitTestAccess() {}

    static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }

    static <S> ValueAwait<S> timedAwait(Callable<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return configured(Await.await(source::call), configuration, clock, parker);
    }

    static <E, C extends Collection<E>> Await<C, E, Void> timedCollectionAwait(
            Callable<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return configured(Await.await(source::call), configuration, clock, parker);
    }

    static <E> Await<Optional<E>, E, Void> timedOptionalAwait(
            Callable<? extends Optional<E>> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return configured(Await.await(source::call), configuration, clock, parker);
    }

    static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>, V> timedMapAwait(
            Callable<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return configured(Await.await(source::call), configuration, clock, parker);
    }

    private static <OBSERVED> ValueAwait<OBSERVED> configured(
            ValueAwait<OBSERVED> await, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return await.usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }

    private static <OBSERVED, ELEMENT, MAP_VALUE> Await<OBSERVED, ELEMENT, MAP_VALUE> configured(
            Await<OBSERVED, ELEMENT, MAP_VALUE> await, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return await.usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }
}
