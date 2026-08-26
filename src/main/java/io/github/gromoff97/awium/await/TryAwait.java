package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedStage;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedSequenceStage;
import io.github.gromoff97.awium.conditioning.conditions.ConditionStage;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class TryAwait<S, E, F extends Source<?>>
        extends AbstractAwait<S, E, F, TryAwait<S, E, F>> {

    private TryAwait(Source<? extends S> source) {
        super(source);
    }

    public static <T> TryAwait<T, T, Source<?>> tryAwait(Source<T> source) {
        return new TryAwait<>(source);
    }

    public static <T> TryAwait<Optional<T>, T, OptionalSource<?>> tryAwait(OptionalSource<T> source) {
        return new TryAwait<>(source);
    }

    public static <E, C extends Collection<E>> TryAwait<C, E, CollectionSource<?>> tryAwait(
            CollectionSource<C> source) {
        return new TryAwait<>(source);
    }

    public static <K, V, M extends Map<K, V>> TryAwait<M, Map.Entry<K, V>, MapSource<?>> tryAwait(
            MapSource<M> source) {
        return new TryAwait<>(source);
    }

    TryAwait(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private TryAwait(TryAwait<S, E, F> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    TryAwait<S, E, F> reconfigured(WaitConfiguration configuration) {
        return new TryAwait<>(this, configuration);
    }

    public AwaitResult<S, S> until(PreservingStage<? super S> condition) {
        return capture(prepare(condition));
    }

    public <R> AwaitResult<S, R> until(ConditionStage<? super S, ? extends R> condition) {
        return capture(prepare(condition));
    }

    public AwaitResult<S, E> until(SelectedStage<? super S, F> condition) {
        return capture(prepare(condition));
    }

    public AwaitResult<S, List<E>> until(
            SelectedSequenceStage<? super S, F> condition) {
        return capture(prepareSelectedSequence(condition));
    }
}
