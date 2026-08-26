package io.github.gromoff97.awium.await;

import io.github.gromoff97.awium.conditioning.conditions.Condition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.PreservingCondition;
import io.github.gromoff97.awium.conditioning.conditions.Condition.SelectedCondition;
import io.github.gromoff97.awium.engine.WaitConfiguration;
import io.github.gromoff97.awium.sources.Source;
import io.github.gromoff97.awium.sources.Source.CollectionSource;
import io.github.gromoff97.awium.sources.Source.MapSource;
import io.github.gromoff97.awium.sources.Source.OptionalSource;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public final class TryAwait<S, E> extends AbstractAwait<S, E, TryAwait<S, E>> {

    private TryAwait(Source<? extends S> source) {
        super(source);
    }

    public static <T> TryAwait<T, T> tryAwait(Source<T> source) {
        return new TryAwait<>(source);
    }

    public static <T> TryAwait<Optional<T>, T> tryAwait(OptionalSource<T> source) {
        return new TryAwait<>(source);
    }

    public static <E, C extends Collection<E>> TryAwait<C, E> tryAwait(
            CollectionSource<C> source) {
        return new TryAwait<>(source);
    }

    public static <K, V, M extends Map<K, V>> TryAwait<M, Map.Entry<K, V>> tryAwait(
            MapSource<M> source) {
        return new TryAwait<>(source);
    }

    TryAwait(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private TryAwait(TryAwait<S, E> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    TryAwait<S, E> reconfigured(WaitConfiguration configuration) {
        return new TryAwait<>(this, configuration);
    }

    public AwaitResult<S, S> until(PreservingCondition<? super S> condition) {
        return capture(prepare(condition));
    }

    public AwaitResult<S, S> until(
            PreservingCondition.ExplainedCondition<? super S> condition) {
        return capture(prepare(condition));
    }

    public <R> AwaitResult<S, R> until(Condition<? super S, ? extends R> condition) {
        return capture(prepare(condition));
    }

    public <R> AwaitResult<S, R> until(
            Condition.ExplainedCondition<? super S, ? extends R> condition) {
        return capture(prepare(condition));
    }

    public AwaitResult<S, E> until(SelectedCondition<? super S> condition) {
        return capture(prepare(condition));
    }

    public AwaitResult<S, E> until(
            SelectedCondition.ExplainedCondition<? super S> condition) {
        return capture(prepare(condition));
    }
}
