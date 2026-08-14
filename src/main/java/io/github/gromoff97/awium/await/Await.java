package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.CollectionSource;
import io.github.gromoff97.awium.sources.MapSource;
import io.github.gromoff97.awium.sources.OptionalSource;
import io.github.gromoff97.awium.sources.Source;

import java.util.Collection;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class Await<S> extends AbstractAwait<S, Await<S>> {

    private Await(Source<? extends S> source) {
        super(source);
    }

    public static <T> Await<T> await(Source<T> source) {
        return new Await<>(source);
    }

    public static <T> OptionalAwait<T> await(OptionalSource<T> source) {
        return new OptionalAwait<>(source);
    }

    public static <C extends Collection<?>> StructuralAwait<C> await(CollectionSource<C> source) {
        return new StructuralAwait<>(source, "collection", Collection::size);
    }

    public static <M extends Map<?, ?>> StructuralAwait<M> await(MapSource<M> source) {
        return new StructuralAwait<>(source, "map", Map::size);
    }

    Await(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private Await(Await<S> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    Await<S> reconfigured(WaitConfiguration configuration) {
        return new Await<>(this, configuration);
    }
}
