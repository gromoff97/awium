package io.github.gromoff97.awium.fluent;

import io.github.gromoff97.awium.internal.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class AwaitTestAccess {

    private AwaitTestAccess() {
        throw new AssertionError("Utility class");
    }

    public static <S> Await<S, S, Source<?>> timedAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new Await<>(source, configuration, clock, parker);
    }

    public static <S> TryAwait<S, S, Source<?>> timedTryAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new TryAwait<>(source, configuration, clock, parker);
    }

    public static <E, C extends Collection<E>> Await<C, E, Source.CollectionSource<?>> timedCollectionAwait(
            Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration, clock, parker);
    }

    public static <E> Await<Optional<E>, E, Source.OptionalSource<?>> timedOptionalAwait(
            Source<? extends Optional<E>> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration, clock, parker);
    }

    public static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>, Source.MapSource<?>> timedMapAwait(
            Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new Await<>(source, configuration, clock, parker);
    }
}
