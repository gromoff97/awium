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

public final class Await<S, E> extends AbstractAwait<S, E, Await<S, E>> {

    private Await(Source<? extends S> source) {
        super(source);
    }

    public static <T> Await<T, T> await(Source<T> source) {
        return new Await<>(source);
    }

    public static <T> Await<Optional<T>, T> await(OptionalSource<T> source) {
        return new Await<>(source);
    }

    public static <E, C extends Collection<E>> Await<C, E> await(CollectionSource<C> source) {
        return new Await<>(source);
    }

    public static <K, V, M extends Map<K, V>> Await<M, Map.Entry<K, V>> await(MapSource<M> source) {
        return new Await<>(source);
    }

    Await(Source<? extends S> source, WaitConfiguration configuration,
            LongSupplier clock, LongConsumer parker) {
        super(source, configuration, clock, parker);
    }

    private Await(Await<S, E> await, WaitConfiguration configuration) {
        super(await, configuration);
    }

    @Override
    Await<S, E> reconfigured(WaitConfiguration configuration) {
        return new Await<>(this, configuration);
    }

    public S until(PreservingCondition<? super S> condition) {
        return complete(prepare(condition));
    }

    public S until(PreservingCondition.ExplainedCondition<? super S> condition) {
        return complete(prepare(condition));
    }

    public <R> R until(Condition<? super S, ? extends R> condition) {
        return complete(prepare(condition));
    }

    public <R> R until(Condition.ExplainedCondition<? super S, ? extends R> condition) {
        return complete(prepare(condition));
    }

    public E until(SelectedCondition<? super S> condition) {
        return complete(prepare(condition));
    }

    public E until(SelectedCondition.ExplainedCondition<? super S> condition) {
        return complete(prepare(condition));
    }
}
