package io.github.gromoff97.awium;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static java.time.Duration.ofNanos;

public final class AwaitTestAccess {

    private AwaitTestAccess() {
        throw new AssertionError("Utility class");
    }

    public static <S> Await<S, S, Source<?>> timedAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return Await.<S>await(source::get).usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }

    public static <E, C extends Collection<E>> Await<C, E, Source.CollectionSource<?>> timedCollectionAwait(
            Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return Await.<E, C>await(source::get).usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }

    public static <E> Await<Optional<E>, E, Source.OptionalSource<?>> timedOptionalAwait(
            Source<? extends Optional<E>> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return Await.<E>await((Source.OptionalSource<E>) source::get).usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }

    public static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>, Source.MapSource<?>> timedMapAwait(
            Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return Await.<K, V, M>await(source::get).usingTime(clock, parker)
                .every(ofNanos(configuration.everyNanos())).upTo(ofNanos(configuration.upToNanos()))
                .persisting(ofNanos(configuration.persistenceNanos()));
    }
}
