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

    public static <S> Await<S> timedAwait(Source<? extends S> source,
            WaitConfiguration configuration, LongSupplier clock,
            LongConsumer parker) {
        return new Await<>(source, configuration, clock, parker);
    }

    public static <C extends Collection<?>> CollectionAwait<C> timedCollectionAwait(
            Source<? extends C> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new CollectionAwait<>(source, configuration, clock, parker);
    }

    public static <M extends Map<?, ?>> MapAwait<M> timedMapAwait(
            Source<? extends M> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        return new MapAwait<>(source, configuration, clock, parker);
    }
}
