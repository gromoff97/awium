package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.time.Duration.ofNanos;

final class AwaitTestAccess {

    private AwaitTestAccess() {}

    static WaitConfiguration config(long every, long upTo, long persistence) {
        return new WaitConfiguration(every, upTo, persistence);
    }

    static <S> Await<S, Object> timedAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return configured(Await.<S>await(source::get), configuration, clock, parker);
    }

    static <E, C extends Collection<E>> Await<C, E> timedCollectionAwait(
            Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return configured(Await.<E, C>await(source::get), configuration, clock, parker);
    }

    static <E> Await<Optional<E>, E> timedOptionalAwait(
            Source<? extends Optional<E>> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return configured(Await.<E>await((Source.OptionalSource<E>) source::get), configuration, clock, parker);
    }

    static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>> timedMapAwait(
            Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return configured(Await.<K, V, M>await(source::get), configuration, clock, parker);
    }

    private static <OBSERVED, ELEMENT> Await<OBSERVED, ELEMENT> configured(
            Await<OBSERVED, ELEMENT> await, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return await.usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }
}
